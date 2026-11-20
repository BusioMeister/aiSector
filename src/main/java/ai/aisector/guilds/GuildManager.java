package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.user.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.google.gson.JsonObject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GuildManager {
    private static final long RELOAD_CACHE_TTL_NANOS = 2_000_000_000L;

    private static final class MissingGuildException extends RuntimeException {
        private MissingGuildException(String tag) {
            super("Guild document no longer exists: " + tag);
        }
    }

    private static final class GuildVersionConflictException extends RuntimeException {
        private GuildVersionConflictException(String tag, long expectedVersion) {
            super("Guild " + tag + " changed after cache version " + expectedVersion);
        }
    }

    private static final class GuildMutationLockBusyException extends RuntimeException {
        private GuildMutationLockBusyException(String tag) {
            super("Another backend is creating or deleting guild " + tag);
        }
    }

    private final MongoDBManager mongo;
    private final SectorPlugin plugin;
    private final Map<String, Guild> guildsByTag = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRefreshByTag = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> persistedVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> mongoVersionsByTag = new ConcurrentHashMap<>();
    private final Map<String, String> mongoGenerationsByTag = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Guild>> pendingReloads = new ConcurrentHashMap<>();
    private final Set<String> deletingTags = ConcurrentHashMap.newKeySet();
    private final WriteFailureFence writeFailureFence = new WriteFailureFence();
    private final CompletableFuture<Void> initialLoad;
    private final ExecutorService persistenceExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2048), runnable -> {
                Thread thread = new Thread(runnable, "aiSector-guild-mongo-write");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public GuildManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.mongo = plugin.getMongoDBManager();
        this.initialLoad = mongo.findManyAsync("guilds", new Document())
                .thenAccept(this::cacheInitialGuilds);
        initialLoad.exceptionally(error -> {
            plugin.getLogger().severe("Could not load guild cache from MongoDB: " + error.getMessage());
            return null;
        });
    }

    private void cacheInitialGuilds(List<Document> documents) {
        long loadedAt = System.nanoTime();
        for (Document document : documents) {
            try {
                Guild guild = fromDocument(document);
                if (guild == null) continue;
                String key = normalizeTag(guild.getTag());
                guildsByTag.put(key, guild);
                lastRefreshByTag.put(key, loadedAt);
                mongoVersionsByTag.put(key, documentVersion(document));
                mongoGenerationsByTag.put(key, documentGeneration(document));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipping invalid guild document: " + exception.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + guildsByTag.size() + " guilds into cache.");
    }

    private MongoCollection<Document> col() {
        return mongo.getCollection("guilds");
    }

    // ===== ŁADOWANIE / CACHE =====

    public boolean isReady() {
        return initialLoad.isDone() && !initialLoad.isCompletedExceptionally();
    }

    public CompletableFuture<Void> whenReady() {
        return initialLoad;
    }

    public Guild getGuild(String tag) {
        String key = normalizeTag(tag);
        return key == null ? null : guildsByTag.get(key);
    }

    public Guild reloadGuild(String tag) {
        String key = normalizeTag(tag);
        if (key == null) return null;
        Guild cached = guildsByTag.get(key);
        Long lastRefresh = lastRefreshByTag.get(key);
        if (cached != null && lastRefresh != null
                && System.nanoTime() - lastRefresh < RELOAD_CACHE_TTL_NANOS) {
            return cached;
        }
        reloadGuildAsync(tag).exceptionally(error -> {
            plugin.getLogger().warning("Could not refresh guild " + tag + ": " + error.getMessage());
            return null;
        });
        return cached;
    }


    public Guild getGuildByMember(UUID uuid) {
        if (uuid == null) return null;
        for (Guild guild : guildsByTag.values()) {
            if (guild.getMembers().contains(uuid)) {
                return guild;
            }
        }
        return null;
    }
    public Guild getGuildCached(String tag) {
        return getGuild(tag); // alias - czytelność
    }

    /** Rank points are independent of editable guild snapshots. Never credit a recreated guild. */
    public CompletableFuture<Boolean> creditPvpRating(Guild winner, UUID killer, Guild loser, UUID victim, int points) {
        if (points <= 0 || winner == null || loser == null || winner.equals(loser)
                || winner.roleOf(killer) == null || loser.roleOf(victim) == null) {
            return CompletableFuture.completedFuture(false);
        }
        Bson winnerIdentity = ratingIdentity(winner, killer);
        Bson loserIdentity = ratingIdentity(loser, victim);
        if (winnerIdentity == null || loserIdentity == null) return CompletableFuture.completedFuture(false);
        return mongo.executeAsync(() -> {
            if (col().find(loserIdentity).projection(new Document("_id", 1)).first() == null) return false;
            return col().updateOne(winnerIdentity, new Document("$inc", new Document("ratingPoints", (long) points)))
                    .getModifiedCount() == 1;
        });
    }

    private static Bson ratingIdentity(Guild guild, UUID member) {
        String generation = guild.getPersistenceGeneration();
        if (generation == null) return null;
        Bson identity = generation.startsWith("legacy:")
                ? (ObjectId.isValid(generation.substring(7)) ? Filters.eq("_id", new ObjectId(generation.substring(7))) : null)
                : Filters.eq("_generation", generation);
        return identity == null ? null : Filters.and(identity, Filters.eq("tag", guild.getTag()),
                Filters.eq("members", member.toString()));
    }

    public boolean isPersistencePending(String tag) {
        String key = normalizeTag(tag);
        return key != null && cacheVersions.getOrDefault(key, 0L) > persistedVersions.getOrDefault(key, 0L);
    }

    public CompletableFuture<Guild> refreshAfterRemoteDeletion(String tag, String deletedGeneration) {
        String key = normalizeTag(tag);
        if (key == null) return CompletableFuture.completedFuture(null);
        return whenReady().thenCompose(ignored -> {
            String localGeneration = mongoGenerationsByTag.get(key);
            if (localGeneration != null && !Objects.equals(localGeneration, deletedGeneration)) {
                return CompletableFuture.completedFuture(guildsByTag.get(key));
            }
            long version = cacheVersions.merge(key, 1L, Long::sum);
            persistedVersions.put(key, version);
            guildsByTag.remove(key);
            lastRefreshByTag.remove(key);
            mongoVersionsByTag.remove(key);
            mongoGenerationsByTag.remove(key);
            pendingReloads.remove(key);
            return reloadGuildAsync(key);
        });
    }

    public CompletableFuture<Guild> reloadGuildAsync(String tag) {
        String key = normalizeTag(tag);
        if (key == null) return CompletableFuture.completedFuture(null);
        if (!initialLoad.isDone()) {
            return initialLoad.thenCompose(ignored -> reloadGuildAsync(tag));
        }
        if (initialLoad.isCompletedExceptionally()) return initialLoad.thenApply(ignored -> null);
        if (deletingTags.contains(key)) return CompletableFuture.completedFuture(null);
        Guild cached = guildsByTag.get(key);
        Long lastRefresh = lastRefreshByTag.get(key);
        long version = cacheVersions.getOrDefault(key, 0L);
        if (version > persistedVersions.getOrDefault(key, 0L) && cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (cached != null && lastRefresh != null
                && System.nanoTime() - lastRefresh < RELOAD_CACHE_TTL_NANOS) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<Guild> pending = pendingReloads.get(key);
        if (pending != null) return pending;

        synchronized (pendingReloads) {
            pending = pendingReloads.get(key);
            if (pending != null) return pending;

            CompletableFuture<Guild> refresh = mongo.findOneAsync("guilds", Filters.regex("tag", tagFilter(tag)))
                    .thenApply(doc -> {
                        if (deletingTags.contains(key) || cacheVersions.getOrDefault(key, 0L) != version
                                || version > persistedVersions.getOrDefault(key, 0L)) {
                            return guildsByTag.get(key);
                        }
                        if (doc == null) {
                            guildsByTag.remove(key);
                            lastRefreshByTag.remove(key);
                            mongoVersionsByTag.remove(key);
                            mongoGenerationsByTag.remove(key);
                            return null;
                        }
                        Guild guild = fromDocument(doc);
                        if (guild == null || guild.getTag() == null) {
                            guildsByTag.remove(key);
                            lastRefreshByTag.remove(key);
                            mongoVersionsByTag.remove(key);
                            mongoGenerationsByTag.remove(key);
                            return null;
                        }
                        String canonicalKey = normalizeTag(guild.getTag());
                        guildsByTag.put(canonicalKey, guild);
                        lastRefreshByTag.put(canonicalKey, System.nanoTime());
                        mongoVersionsByTag.put(canonicalKey, documentVersion(doc));
                        mongoGenerationsByTag.put(canonicalKey, documentGeneration(doc));
                        return guild;
                    });
            pendingReloads.put(key, refresh);
            refresh.whenComplete((guild, error) -> pendingReloads.remove(key, refresh));
            return refresh;
        }
    }

    public boolean exists(String tag) {
        return getGuild(tag) != null;
    }

    public Collection<Guild> getAllGuilds() {
        return Collections.unmodifiableCollection(guildsByTag.values());
    }

    // ===== CRUD GILDII =====

    /** Creates the guild under a short-lived Redis lock shared by all backends. */
    public CompletableFuture<Guild> createGuildAsync(Guild guild) {
        if (guild == null || !isValidPersistentTag(guild.getTag()) || guild.getOwner() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid guild data"));
        }
        String key = normalizeTag(guild.getTag());
        if (!initialLoad.isDone()) {
            return initialLoad.thenCompose(ignored -> createGuildAsync(guild));
        }
        if (initialLoad.isCompletedExceptionally()) return initialLoad.thenApply(ignored -> null);

        String lockKey = "aisector:guild:create:" + key;
        String token = UUID.randomUUID().toString();
        return plugin.getRedisManager().executeAsync(jedis ->
                        jedis.set(lockKey, token, SetParams.setParams().nx().px(120_000)))
                .thenCompose(acquired -> {
                    if (!"OK".equals(acquired)) return CompletableFuture.completedFuture(null);

                    CompletableFuture<Guild> creation = mongo.findOneAsync("guilds",
                                    Filters.regex("tag", tagFilter(key)))
                            .thenCompose(existing -> {
                                if (existing != null) {
                                    Guild existingGuild = fromDocument(existing);
                                    if (existingGuild != null) {
                                        String existingKey = normalizeTag(existingGuild.getTag());
                                        guildsByTag.put(existingKey, existingGuild);
                                        lastRefreshByTag.put(existingKey, System.nanoTime());
                                        mongoVersionsByTag.put(existingKey, documentVersion(existing));
                                        mongoGenerationsByTag.put(existingKey, documentGeneration(existing));
                                    }
                                    return CompletableFuture.completedFuture(null);
                                }

                                String generation = UUID.randomUUID().toString();
                                Document snapshot = toDocument(guild)
                                        .append("_id", "guild:" + key)
                                        .append("_version", 0L)
                                        .append("_generation", generation);
                                return mongo.insertOneAsync("guilds", snapshot)
                                        .thenApply(ignored -> {
                                            long version = cacheVersions.merge(key, 1L, Long::sum);
                                            persistedVersions.put(key, version);
                                            mongoVersionsByTag.put(key, 0L);
                                            mongoGenerationsByTag.put(key, generation);
                                            guild.setPersistenceGeneration(generation);
                                            guildsByTag.put(key, guild);
                                            lastRefreshByTag.put(key, System.nanoTime());
                                            return guild;
                                        });
                            });
                    creation.whenComplete((created, error) -> releaseCreateLock(lockKey, token));
                    return creation;
                });
    }

    private void releaseCreateLock(String lockKey, String token) {
        String releaseScript = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) else return 0 end";
        plugin.getRedisManager().executeAsync(jedis ->
                        jedis.eval(releaseScript, Collections.singletonList(lockKey), Collections.singletonList(token)))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not release guild creation lock " + lockKey + ": "
                            + error.getMessage());
                    return null;
                });
    }


    public CompletableFuture<Void> saveGuild(Guild guild) {
        if (guild == null) return CompletableFuture.completedFuture(null);
        if (!isValidPersistentTag(guild.getTag())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid guild tag"));
        }
        Document doc = toDocument(guild);
        String key = normalizeTag(guild.getTag());
        String expectedGeneration = guild.getPersistenceGeneration();
        if (deletingTags.contains(key)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild deletion is in progress: " + key));
        }
        long version = cacheVersions.merge(key, 1L, Long::sum);
        guildsByTag.put(key, guild);
        lastRefreshByTag.put(key, System.nanoTime());
        return enqueuePersistence(key, version, guild.getOwner(), () -> withGuildMutationLock(key, jedis -> {
            long expectedMongoVersion = mongoVersionsByTag.getOrDefault(key, 0L);
            UpdateResult result = col().updateOne(
                    guildIdentityFilter(guild.getTag(), expectedMongoVersion, expectedGeneration),
                    versionedGuildUpdate(doc));
            if (result.getMatchedCount() == 0) {
                if (col().find(Filters.eq("tag", guild.getTag())).first() == null) {
                    throw new MissingGuildException(guild.getTag());
                }
                throw new GuildVersionConflictException(guild.getTag(), expectedMongoVersion);
            }
            mongoVersionsByTag.put(key, expectedMongoVersion + 1L);
        }));
    }

    public CompletableFuture<Boolean> deleteGuildAsync(Guild guild) {
        if (guild == null) return CompletableFuture.completedFuture(false);
        if (!isValidPersistentTag(guild.getTag())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid guild tag"));
        }
        String key = normalizeTag(guild.getTag());
        String expectedGeneration = guild.getPersistenceGeneration();
        if (!deletingTags.add(key)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild deletion is already in progress: " + key));
        }
        long version = cacheVersions.merge(key, 1L, Long::sum);
        guildsByTag.remove(key);
        lastRefreshByTag.remove(key);
        CompletableFuture<Boolean> deletion = enqueuePersistence(key, version, guild.getOwner(), () -> {
            withGuildMutationLock(key, jedis -> {
                long expectedMongoVersion = mongoVersionsByTag.getOrDefault(key, 0L);
                Document existingDocument = col().find(Filters.eq("tag", guild.getTag())).first();
                String generation = existingDocument == null
                        ? mongoGenerationsByTag.getOrDefault(key, "legacy:" + key)
                        : documentGeneration(existingDocument);
                List<Document> observedProfiles = mongo.getCollection("users")
                        .find(Filters.regex("guildTag", tagFilter(guild.getTag())))
                        .projection(new Document("_id", 1).append("guildTag", 1).append("guildMembershipRevision", 1))
                        .into(new ArrayList<>());
                DeleteResult result = col().deleteOne(
                        guildIdentityFilter(guild.getTag(), expectedMongoVersion, expectedGeneration));
                if (result.getDeletedCount() == 0
                        && col().find(Filters.eq("tag", guild.getTag())).first() != null) {
                    throw new GuildVersionConflictException(guild.getTag(), expectedMongoVersion);
                }
                try {
                    // A paused old delete must not clear assignments made after tag recreation / lease loss.
                    // Only clean after this invocation actually deleted its observed guild generation.
                    if (result.getDeletedCount() > 0) {
                        for (int start = 0; start < observedProfiles.size(); start += 500) {
                            List<Document> batch = observedProfiles.subList(start, Math.min(start + 500, observedProfiles.size()));
                            mongo.getCollection("users").updateMany(
                                    Filters.or(batch.stream().map(GuildProfileCleanup::observedAssignment).toList()),
                                    new Document("$set", new Document("guildTag", null).append("guildRole", null)
                                            .append("guildGeneration", null)
                                            .append("guildMembershipRevision", UUID.randomUUID().toString())));
                        }
                    }
                } catch (RuntimeException profileError) {
                    plugin.getLogger().severe("Guild " + key + " was deleted, but offline member profiles "
                            + "could not be cleared: " + profileError.getMessage());
                }
                mongoVersionsByTag.remove(key);
                mongoGenerationsByTag.remove(key);
                if (cacheVersions.getOrDefault(key, 0L) == version) {
                    guildsByTag.remove(key);
                    lastRefreshByTag.remove(key);
                    persistedVersions.merge(key, version, Math::max);
                }
                if (result.getDeletedCount() > 0) {
                    JsonObject invalidation = new JsonObject();
                    invalidation.addProperty("tag", key);
                    invalidation.addProperty("generation", generation);
                    try {
                        jedis.publish(RedisChannels.GUILD_DELETED, invalidation.toString());
                    } catch (RuntimeException publishError) {
                        plugin.getLogger().severe("Guild " + key + " was deleted but cache invalidation "
                                + "could not be published: " + publishError.getMessage());
                    }
                }
            });
        }).thenApply(ignored -> true);
        deletion.whenComplete((deleted, error) -> {
            deletingTags.remove(key);
            if (error != null) {
                reloadGuildAsync(key).whenComplete((currentGuild, refreshError) -> {
                    if (refreshError != null) {
                        plugin.getLogger().warning("Could not reload guild after delete conflict for " + key + ": "
                                + refreshError.getMessage());
                    } else {
                        reconcileOnlineGuildMembers(key);
                    }
                });
            }
        });
        return deletion;
    }

    private void withGuildMutationLock(String tag, Consumer<Jedis> mutation) {
        String lockKey = "aisector:guild:create:" + tag;
        String token = UUID.randomUUID().toString();
        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String acquired = null;
            do {
                acquired = jedis.set(lockKey, token, SetParams.setParams().nx().px(120_000));
                if ("OK".equals(acquired)) break;
                Thread.sleep(100L);
            } while (System.nanoTime() < deadline);
            if (!"OK".equals(acquired)) throw new GuildMutationLockBusyException(tag);
            try {
                mutation.accept(jedis);
            } finally {
                String releaseScript = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) else return 0 end";
                try {
                    jedis.eval(releaseScript, Collections.singletonList(lockKey), Collections.singletonList(token));
                } catch (RuntimeException releaseError) {
                    plugin.getLogger().warning("Could not release guild mutation lock " + lockKey + ": "
                            + releaseError.getMessage());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GuildMutationLockBusyException(tag);
        }
    }

    private CompletableFuture<Void> enqueuePersistence(String key, long version, UUID ownerUuid, Runnable operation) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> {
                if (writeFailureFence.rejects(key, version)) {
                    result.completeExceptionally(new IllegalStateException("Discarded queued stale guild snapshot " + key));
                    return;
                }
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        operation.run();
                        persistedVersions.merge(key, version, Math::max);
                        result.complete(null);
                        return;
                    } catch (MissingGuildException exception) {
                        invalidateQueuedSnapshots(key);
                        if (cacheVersions.getOrDefault(key, 0L) == version) {
                            guildsByTag.remove(key);
                            lastRefreshByTag.remove(key);
                        }
                        persistedVersions.merge(key, version, Math::max);
                        plugin.getLogger().warning("Rejected stale guild save for " + key + ": "
                                + exception.getMessage());
                        reconcileOnlineGuildMembers(key);
                        result.completeExceptionally(exception);
                        return;
                    } catch (GuildVersionConflictException exception) {
                        invalidateQueuedSnapshots(key);
                        if (cacheVersions.getOrDefault(key, 0L) == version) {
                            guildsByTag.remove(key);
                            lastRefreshByTag.remove(key);
                            mongoVersionsByTag.remove(key);
                        }
                        persistedVersions.merge(key, version, Math::max);
                        plugin.getLogger().warning("Rejected concurrent stale guild update: " + exception.getMessage());
                        reloadGuildAsync(key).whenComplete((freshGuild, refreshError) -> {
                            if (refreshError != null) {
                                plugin.getLogger().warning("Could not reload guild after write conflict for " + key
                                        + ": " + refreshError.getMessage());
                            } else if (!deletingTags.contains(key)) {
                                reconcileOnlineGuildMembers(key);
                            }
                        });
                        result.completeExceptionally(exception);
                        return;
                    } catch (RuntimeException exception) {
                        if (attempt == 3) {
                            invalidateQueuedSnapshots(key);
                            if (cacheVersions.getOrDefault(key, 0L) == version) {
                                guildsByTag.remove(key);
                                lastRefreshByTag.remove(key);
                                mongoVersionsByTag.remove(key);
                            }
                            persistedVersions.merge(key, version, Math::max);
                            plugin.getLogger().severe("Could not persist guild " + key + " after " + attempt
                                    + " attempts: " + exception.getMessage());
                            reloadGuildAsync(key).whenComplete((currentGuild, reloadError) -> {
                                if (reloadError != null) {
                                    plugin.getLogger().warning("Could not restore guild cache after failed write for "
                                            + key + ": " + reloadError.getMessage());
                                } else {
                                    reconcileOnlineGuildMembers(key);
                                }
                            });
                            result.completeExceptionally(exception);
                            return;
                        }
                        try {
                            Thread.sleep(250L * attempt);
                        } catch (InterruptedException interrupted) {
                            invalidateQueuedSnapshots(key);
                            Thread.currentThread().interrupt();
                            plugin.getLogger().severe("Interrupted while retrying guild persistence for " + key);
                            result.completeExceptionally(interrupted);
                            return;
                        }
                    }
                }
            });
        } catch (RuntimeException exception) {
            invalidateQueuedSnapshots(key);
            plugin.getLogger().severe("Guild persistence queue is unavailable for " + key + ": "
                    + exception.getMessage());
            reloadGuildAsync(key).whenComplete((guild, refreshError) -> {
                if (refreshError == null) reconcileOnlineGuildMembers(key);
                else plugin.getLogger().warning("Could not reload guild after queue rejection: " + key);
            });
            result.completeExceptionally(exception);
        }
        result.whenComplete((ignored, error) -> {
            if (error != null && ownerUuid != null && cacheVersions.getOrDefault(key, 0L) == version) {
                notifyOwnerOfPersistenceFailure(ownerUuid, key, error);
            }
        });
        return result;
    }

    private void invalidateQueuedSnapshots(String key) {
        long through = cacheVersions.getOrDefault(key, 0L);
        writeFailureFence.invalidateThrough(key, through);
        persistedVersions.merge(key, through, Math::max);
        guildsByTag.remove(key);
        lastRefreshByTag.remove(key);
        // Retain the observed Mongo version until an authoritative reload replaces it.
    }

    private void notifyOwnerOfPersistenceFailure(UUID ownerUuid, String tag, Throwable error) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player owner = Bukkit.getPlayer(ownerUuid);
                if (owner != null) {
                    owner.sendMessage("§cZmiany gildii [" + tag + "] nie zostały zapisane. "
                            + "Spróbuj ponowić operację za chwilę.");
                }
            });
        } catch (RuntimeException ignoredShutdown) {
            plugin.getLogger().warning("Guild save failed during shutdown for " + tag + ": " + error.getMessage());
        }
    }

    private void reconcileOnlineGuildMembers(String tag) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    User user = plugin.getUserManager().getUser(player.getUniqueId());
                    if (user == null) continue;
                    Guild actualGuild = getGuildByMember(player.getUniqueId());
                    if (actualGuild == null) {
                        if (tag.equalsIgnoreCase(user.getGuildTag())) {
                            user.setGuildTag(null);
                            user.setGuildRole(null);
                        }
                    } else if (player.getUniqueId().equals(actualGuild.getOwner())) {
                        user.setGuildTag(actualGuild.getTag());
                        user.setGuildRole("OWNER");
                    } else {
                        user.setGuildTag(actualGuild.getTag());
                        if (actualGuild.getMods().contains(player.getUniqueId())) {
                            user.setGuildRole("MOD");
                        } else {
                            user.setGuildRole("MEMBER");
                        }
                    }
                }
                if (plugin.getGuildTagManager() != null) plugin.getGuildTagManager().refreshAllOnline();
            });
        } catch (RuntimeException ignoredShutdown) {
            // Cache reconciliation is not needed after the server has begun shutting down.
        }
    }

    public static String normalizeTag(String tag) {
        return tag == null ? null : tag.trim().toUpperCase(Locale.ROOT);
    }

    static boolean isValidPersistentTag(String tag) {
        String normalized = normalizeTag(tag);
        return normalized != null && !normalized.isBlank() && normalized.length() <= 32
                && normalized.indexOf(':') < 0
                && normalized.chars().noneMatch(Character::isISOControl);
    }

    public static boolean containsTag(Collection<String> tags, String tag) {
        String normalized = normalizeTag(tag);
        return normalized != null && tags != null
                && tags.stream().anyMatch(value -> normalized.equals(normalizeTag(value)));
    }

    private static java.util.regex.Pattern tagFilter(String tag) {
        return java.util.regex.Pattern.compile("^" + Pattern.quote(tag.trim()) + "$", Pattern.CASE_INSENSITIVE);
    }

    static Bson guildVersionFilter(long expectedVersion) {
        return expectedVersion == 0L
                ? Filters.or(Filters.eq("_version", 0L), Filters.exists("_version", false))
                : Filters.eq("_version", expectedVersion);
    }

    static Bson guildIdentityFilter(String tag, long version, String generation) {
        Bson identity;
        if (generation == null) {
            identity = Filters.eq("_id", "missing-generation:" + normalizeTag(tag));
        } else if (generation.startsWith("legacy:") && ObjectId.isValid(generation.substring(7))) {
            identity = Filters.eq("_id", new ObjectId(generation.substring(7)));
        } else if (generation.startsWith("legacy:")) {
            identity = Filters.exists("_generation", false);
        } else {
            identity = Filters.eq("_generation", generation);
        }
        return Filters.and(Filters.eq("tag", tag), guildVersionFilter(version), identity);
    }

    static Document versionedGuildUpdate(Document snapshot) {
        return new Document("$set", snapshot).append("$inc", new Document("_version", 1L));
    }

    private long documentVersion(Document document) {
        Object version = document.get("_version");
        return version instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private String documentGeneration(Document document) {
        String generation = document.getString("_generation");
        if (generation != null && !generation.isBlank()) return generation;
        ObjectId id = document.getObjectId("_id");
        return id == null ? "legacy:" + normalizeTag(document.getString("tag")) : "legacy:" + id.toHexString();
    }

    // ===== KONWERSJA <-> DOCUMENT =====

    private Guild fromDocument(Document doc) {
        if (doc == null) return null;

        String tag = doc.getString("tag");
        if (tag == null || tag.isBlank()) return null;
        String name = doc.getString("name");

        String ownerStr = doc.getString("owner");
        UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;

        Set<UUID> members = toUuidSet(doc.getList("members", String.class));
        Set<UUID> mods = toUuidSet(doc.getList("mods", String.class));
        Set<UUID> invitations = toUuidSet(doc.getList("invitations", String.class));

        Set<String> allied = new HashSet<>(
                doc.getList("alliedGuilds", String.class, Collections.emptyList())
        );
        Set<String> allyInv = new HashSet<>(
                doc.getList("allyInvitations", String.class, Collections.emptyList())
        );

        Guild guild = new Guild(tag, name, owner);
        guild.setPersistenceGeneration(documentGeneration(doc));

        guild.getMembers().addAll(members);
        guild.getMods().addAll(mods);
        guild.getInvitations().addAll(invitations);
        guild.getAlliedGuilds().addAll(allied);
        guild.getAllyInvitations().addAll(allyInv);
        guild.setHomeSector(doc.getString("homeSector"));
        guild.setHomeWorld(doc.getString("homeWorld"));
        guild.setHomeX(doc.getDouble("homeX") != null ? doc.getDouble("homeX") : 0.0);
        guild.setHomeY(doc.getDouble("homeY") != null ? doc.getDouble("homeY") : 0.0);
        guild.setHomeZ(doc.getDouble("homeZ") != null ? doc.getDouble("homeZ") : 0.0);
        Double yaw = doc.getDouble("homeYaw");
        Double pitch = doc.getDouble("homePitch");
        guild.setHomeYaw(yaw != null ? yaw.floatValue() : 0.0f);
        guild.setHomePitch(pitch != null ? pitch.floatValue() : 0.0f);
        String deputyStr = doc.getString("deputy");
        guild.setDeputy(deputyStr != null ? java.util.UUID.fromString(deputyStr) : null);

        guild.setFriendlyFireGuild(doc.getBoolean("friendlyFireGuild", false));
        guild.setFriendlyFireAllies(doc.getBoolean("friendlyFireAllies", false));

        guild.setAlertMessage(doc.getString("alertMessage"));
        guild.setWelcomeMessage(doc.getString("welcomeMessage"));

        return guild;
    }


    private Document toDocument(Guild guild) {
        return new Document("tag", guild.getTag())
                .append("name", guild.getName())
                .append("owner", guild.getOwner() != null ? guild.getOwner().toString() : null)
                .append("members", toStringList(guild.getMembers()))
                .append("mods", toStringList(guild.getMods()))
                .append("invitations", toStringList(guild.getInvitations()))
                .append("alliedGuilds", new ArrayList<>(guild.getAlliedGuilds()))
                .append("allyInvitations", new ArrayList<>(guild.getAllyInvitations()))
                // DOM GILDII:
                .append("deputy", guild.getDeputy() != null ? guild.getDeputy().toString() : null)
                .append("friendlyFireGuild", guild.isFriendlyFireGuild())
                .append("friendlyFireAllies", guild.isFriendlyFireAllies())
                .append("alertMessage", guild.getAlertMessage())
                .append("welcomeMessage", guild.getWelcomeMessage())
                .append("homeSector", guild.getHomeSector())
                .append("homeWorld", guild.getHomeWorld())
                .append("homeX", guild.getHomeX())
                .append("homeY", guild.getHomeY())
                .append("homeZ", guild.getHomeZ())
                .append("homeYaw", guild.getHomeYaw())
                .append("homePitch", guild.getHomePitch());
    }
    public void saveAll() {
        for (Guild g : getAllGuilds()) {
            saveGuild(g);
        }
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(35, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for queued guild writes during shutdown.");
                persistenceExecutor.shutdownNow();
                if (!persistenceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("Guild writes remain active during shutdown; MongoDB may close early.");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            persistenceExecutor.shutdownNow();
        }
    }



    private Set<UUID> toUuidSet(List<String> list) {
        if (list == null) return new HashSet<>();
        return list.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    private List<String> toStringList(Set<UUID> set) {
        return set.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
    }
}
