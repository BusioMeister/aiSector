package ai.aisector.user;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.database.MongoDBManager;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class UserManager {
    private record StoredProfile(Document user, List<Document> homes) { }
    private record PendingLoad(Player player, CompletableFuture<User> future) { }

    private final Map<UUID, User> onlineUsers = new ConcurrentHashMap<>();
    private final Map<UUID, Player> arrivingPlayers = new ConcurrentHashMap<>();

    public void beginArrival(Player player) { arrivingPlayers.put(player.getUniqueId(), player); }
    public void finishArrival(Player player) { arrivingPlayers.remove(player.getUniqueId(), player); }
    public boolean isArriving(Player player) { return arrivingPlayers.get(player.getUniqueId()) == player; }
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final Gson gson = new Gson();
    private final TransferSnapshotGuard transferGuard;
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Map<CompletableFuture<?>, Runnable> cacheHandoffs = new ConcurrentHashMap<>();
    private volatile boolean closing;
    private final Set<CompletableFuture<Void>> pendingWrites = ConcurrentHashMap.newKeySet();
    private final PerKeyAsyncQueue<UUID> profileOperations = new PerKeyAsyncQueue<>();
    private final ExecutorService profileLoader = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2048), runnable -> {
                Thread thread = new Thread(runnable, "aiSector-profile-loader");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public UserManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.mongoDBManager = plugin.getMongoDBManager();
        this.transferGuard = new TransferSnapshotGuard(plugin);
    }

    /** Captures Bukkit player state; call on the server thread before asynchronous Redis I/O. */
    public String serializePlayerDataForTransfer(Player player, Location targetLocation) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targetLocation, "targetLocation");
        if (targetLocation.getWorld() == null) {
            throw new IllegalArgumentException("Transfer location must have a loaded world");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dataVersion", 2);
        data.put("x", targetLocation.getX());
        data.put("y", targetLocation.getY());
        data.put("z", targetLocation.getZ());
        data.put("yaw", targetLocation.getYaw());
        data.put("pitch", targetLocation.getPitch());
        data.put("world", targetLocation.getWorld().getName());
        data.put("health", player.getHealth());
        data.put("hunger", player.getFoodLevel());
        data.put("saturation", player.getSaturation());
        data.put("experience", player.getTotalExperience());
        data.put("level", player.getLevel());
        data.put("exp", player.getExp());
        data.put("gameMode", player.getGameMode().name());
        data.put("flying", player.isFlying());
        // Armor and offhand already have their own fields; do not encode them twice.
        data.put("inventory", itemStackArrayToBase64(player.getInventory().getStorageContents()));
        data.put("armor", itemStackArrayToBase64(player.getInventory().getArmorContents()));
        data.put("offhand", itemStackToBase64(player.getInventory().getItemInOffHand()));
        data.put("effects", serializePotionEffects(player.getActivePotionEffects()));
        data.put("heldSlot", player.getInventory().getHeldItemSlot());
        return gson.toJson(data);
    }
    public User loadOrGetUser(Player player) {
        User existing = onlineUsers.get(player.getUniqueId());
        if (existing != null) return existing;

        // Never block the server thread on a cache miss. Callers must tolerate null until the async load completes.
        loadUser(player);
        return onlineUsers.get(player.getUniqueId());
    }
    // cache-only: nie dotyka Mongo
    public User getUser(UUID uuid) {
        if (uuid == null) return null;
        return (User) onlineUsers.get(uuid);
    }



    public void loadUser(Player player) {
        // Compatibility entry point; the actual Mongo read is always asynchronous.
        String playerName = player.getName();
        loadUserAsync(player).exceptionally(error -> {
            plugin.getLogger().warning("Could not asynchronously load profile for " + playerName + ": "
                    + error.getMessage());
            return null;
        });
    }

    /** Loads MongoDB data off-thread and builds the Bukkit-backed user cache on the main thread. */
    public CompletableFuture<User> loadUserAsync(Player player) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("UserManager is shutting down"));
        UUID uuid = player.getUniqueId();
        User cachedUser = onlineUsers.get(uuid);
        if (cachedUser != null) {
            if (cachedUser.getSessionStartMillis() == 0L) {
                cachedUser.setSessionStartMillis(System.currentTimeMillis());
            }
            return CompletableFuture.completedFuture(cachedUser);
        }

        PendingLoad pending = pendingLoads.get(uuid);
        if (pending != null && pending.player() == player) return pending.future().copy();
        CompletableFuture<User> result = new CompletableFuture<>();
        PendingLoad load = new PendingLoad(player, result);
        pendingLoads.put(uuid, load);

        profileOperations.submitResult(uuid, () -> loadProfileFromMongo(player))
                .whenComplete((user, loadError) -> {
                    pendingLoads.remove(uuid, load);
                    if (loadError == null) result.complete(user);
                    else result.completeExceptionally(loadError);
                });
        return result.copy();
    }

    private CompletableFuture<User> loadProfileFromMongo(Player expectedPlayer) {
        UUID uuid = expectedPlayer.getUniqueId();
        String uuidString = uuid.toString();
        CompletableFuture<Document> userDocument = mongoDBManager.findOneAsync("users",
                Filters.eq("uuid", uuidString));
        CompletableFuture<List<Document>> homes = mongoDBManager.findManyAsync("player_homes",
                Filters.eq("player_uuid", uuidString));
        return userDocument.thenCombine(homes, StoredProfile::new).thenCompose(stored -> {
            CompletableFuture<User> result = new CompletableFuture<>();
            cacheHandoffs.put(result, () -> result.completeExceptionally(new IllegalStateException("UserManager is shutting down")));
            result.whenComplete((ignored, error) -> cacheHandoffs.remove(result));
            if (closing) {
                result.completeExceptionally(new IllegalStateException("UserManager is shutting down"));
                return result;
            }
            boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                if (result.isDone() || closing) return;
                try {
                    Player currentPlayer = Bukkit.getPlayer(uuid);
                    if (currentPlayer != expectedPlayer || !expectedPlayer.isOnline()) {
                        throw new IllegalStateException("Player disconnected while profile was loading");
                    }

                    User user = onlineUsers.get(uuid);
                    if (user == null) {
                        user = buildUser(currentPlayer, stored.user(), stored.homes());
                        onlineUsers.put(uuid, user);
                    }
                    if (user.getSessionStartMillis() == 0L) {
                        user.setSessionStartMillis(System.currentTimeMillis());
                    }
                    result.complete(user);
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
            if (!scheduled) {
                result.completeExceptionally(new IllegalStateException("Could not schedule profile cache update"));
            }
            return result;
        });
    }

    private User buildUser(Player player, Document userDoc, List<Document> homeDocs) {
        User user = new User(player.getUniqueId());

        if (userDoc != null) {
            user.setGodMode(userDoc.getBoolean("godMode", false));
            user.setFlying(userDoc.getBoolean("flying", false));
            user.setVanished(userDoc.getBoolean("vanished", false));
            user.setCombatBypass(userDoc.getBoolean("combatBypass", false));
// ====== GILDIA ======
            user.setGuildTag(userDoc.getString("guildTag"));
            user.setGuildRole(userDoc.getString("guildRole"));

            if (userDoc.getDouble("walkSpeed") != null) {
                user.setWalkSpeed(userDoc.getDouble("walkSpeed").floatValue());
            } else {
                user.setWalkSpeed(0.2f);
            }

            if (userDoc.getDouble("flySpeed") != null) {
                user.setFlySpeed(userDoc.getDouble("flySpeed").floatValue());
            } else {
                user.setFlySpeed(0.1f);
            }

            user.setMiningLevel(userDoc.getInteger("miningLevel", 1));

            // ====== MINING EXPERIENCE ======
            Object expObject = userDoc.get("miningExperience");
            if (expObject instanceof Number) {
                user.setMiningExperience(((Number) expObject).longValue());
            } else {
                user.setMiningExperience(0L);
            }

            // ====== MINED BLOCKS ======
            Document minedBlocksDoc = userDoc.get("minedBlocks", Document.class);
            if (minedBlocksDoc != null) {
                for (String key : minedBlocksDoc.keySet()) {
                    try {
                        Material material = Material.valueOf(key);
                        int count = minedBlocksDoc.getInteger(key, 0);
                        user.getMinedBlocks().put(material, count);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // ====== DROP SETTINGS ======
            Document dropSettingsDoc = userDoc.get("dropSettings", Document.class);
            if (dropSettingsDoc != null) {
                for (String key : dropSettingsDoc.keySet()) {
                    try {
                        Material material = Material.valueOf(key);
                        user.setDropEnabled(material, dropSettingsDoc.getBoolean(key));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            user.setCobblestoneDropEnabled(userDoc.getBoolean("cobblestoneDropEnabled", true));

            // ====== NOWE POLA PVP / CZAS GRY ======
            user.setKills(userDoc.getInteger("kills", 0));
            user.setDeaths(userDoc.getInteger("deaths", 0));
            user.setRating(ai.aisector.ranking.Rating.read(userDoc.get("rating"), ai.aisector.ranking.Rating.INITIAL));
            user.setKillstreak(ai.aisector.ranking.Rating.read(userDoc.get("killstreak"), 0));

            Object playTimeObj = userDoc.get("playTimeSeconds");
            if (playTimeObj instanceof Number) {
                user.setPlayTimeSeconds(((Number) playTimeObj).longValue());
            } else {
                user.setPlayTimeSeconds(0L);
            }
            // ======================================
        }

        if (homeDocs == null) return user;
        for (Document doc : homeDocs) {
            try {
                StoredHomeData stored = StoredHomeData.parse(doc);
                World world = Bukkit.getWorld(stored.world());
                if (world == null) continue;
                Location loc = new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), stored.pitch());
                user.addHome(new Home(stored.slot(), stored.sector(), loc));
            } catch (RuntimeException malformedHome) {
                plugin.getLogger().warning("Skipping malformed stored home for " + player.getUniqueId());
            }
        }

        return user;
    }

    public void unloadUser(Player player) {
        UUID playerUuid = player.getUniqueId();
        PendingLoad pending = pendingLoads.get(playerUuid);
        if (pending != null && pending.player() == player && pendingLoads.remove(playerUuid, pending)) {
            pending.future().completeExceptionally(new IllegalStateException("Player disconnected while loading"));
        }
        String playerName = player.getName();
        User user = onlineUsers.get(playerUuid);
        if (user == null) return;

        if (transferGuard.finish(player)) {
            // The sealed source was already persisted before publish/ACK. A late quit must not
            // overwrite changes made by the destination after it loaded that profile.
            onlineUsers.remove(playerUuid, user);
            return;
        }

        Document update = createUserUpdate(player, user);
        user.setSessionStartMillis(0L);
        CompletableFuture<Void> write = queueProfileWrite(playerUuid,
                () -> persistUserUpdateWithGuildLock(playerUuid, update));
        pendingWrites.add(write);
        write.whenComplete((ignored, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                plugin.getLogger().severe("Could not save profile for " + playerName
                        + "; keeping it in memory for retry: " + error.getMessage());
            } else {
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (user.getSessionStartMillis() == 0L) onlineUsers.remove(playerUuid, user);
                });
            }
        });
    }

    private void persistUserUpdateWithGuildLock(UUID playerUuid, Document update) {
        Document set = update.get("$set", Document.class);
        if (set == null || !set.containsKey("guildTag")) {
            mongoDBManager.updateOneByUuid("users", playerUuid.toString(), update);
            return;
        }

        String guildTag = set.getString("guildTag");
        mongoDBManager.updateOneByUuid("users", playerUuid.toString(),
                new Document("$setOnInsert", new Document("uuid", playerUuid.toString())));
        List<Document> profiles = mongoDBManager.findMany("users", Filters.eq("uuid", playerUuid.toString()), null, 2);
        if (profiles.size() != 1) throw new IllegalStateException("Ambiguous profile identity; manual review required");
        Document observedProfile = profiles.getFirst();
        String expectedTag = update.getString("_expectedGuildTag");
        update.remove("_expectedGuildTag");
        if (expectedTag != null && !expectedTag.equalsIgnoreCase(observedProfile.getString("guildTag")))
            throw new IllegalStateException("Membership changed since the guild command started");
        if (guildTag == null || guildTag.isBlank()) {
            set.put("guildRole", null);
            set.put("guildGeneration", null);
            writeObservedMembership(observedProfile, update);
            return;
        }

        String normalizedTag = guildTag.trim().toUpperCase(Locale.ROOT);
        String lockKey = "aisector:guild:create:" + normalizedTag;
        String token = UUID.randomUUID().toString();
        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String acquired = null;
            do {
                acquired = jedis.set(lockKey, token, SetParams.setParams().nx().px(120_000));
                if ("OK".equals(acquired)) break;
                Thread.sleep(100L);
            } while (System.nanoTime() < deadline);
            if (!"OK".equals(acquired)) {
                throw new IllegalStateException("Timed out waiting for guild mutation lock " + normalizedTag);
            }

            try {
                Pattern exactTag = Pattern.compile("^" + Pattern.quote(normalizedTag) + "$",
                        Pattern.CASE_INSENSITIVE);
                Document guildDocument = mongoDBManager.findOne("guilds", Filters.regex("tag", exactTag));
                VerifiedGuildMembership membership = VerifiedGuildMembership.from(playerUuid, guildDocument);
                String observedTag = observedProfile.getString("guildTag");
                if (membership.tag() == null && observedTag != null && !observedTag.equalsIgnoreCase(guildTag))
                    throw new IllegalStateException("A stale guild write must not clear a different assignment");
                set.put("guildTag", membership.tag());
                set.put("guildRole", membership.role());
                set.put("guildGeneration", membership.generation());
                writeObservedMembership(observedProfile, update);
                VerifiedGuildMembership confirmed = VerifiedGuildMembership.from(playerUuid,
                        mongoDBManager.findOne("guilds", Filters.regex("tag", exactTag)));
                if (!membership.equals(confirmed)) {
                    // A delete/recreate may have happened after the lease expired. Roll back only our own
                    // still-current assignment; never clear a newer writer's revision.
                    mongoDBManager.getCollection("users").updateOne(Filters.and(
                                    Filters.eq("_id", observedProfile.get("_id")),
                                    Filters.eq("guildMembershipRevision", set.get("guildMembershipRevision"))),
                            new Document("$set", new Document("guildTag", null).append("guildRole", null)
                                    .append("guildGeneration", null).append("guildMembershipRevision", UUID.randomUUID().toString())));
                    throw new IllegalStateException("Guild changed during membership assignment; retry with current guild data");
                }
            } finally {
                String releaseScript = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) else return 0 end";
                try {
                    jedis.eval(releaseScript, java.util.Collections.singletonList(lockKey),
                            java.util.Collections.singletonList(token));
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not release guild profile lock " + lockKey + ": "
                            + exception.getMessage());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for guild profile lock " + normalizedTag,
                    exception);
        }
    }

    private void writeObservedMembership(Document observed, Document update) {
        if (mongoDBManager.getCollection("users").updateOne(ProfileMembershipVersion.observed(observed), update)
                .getMatchedCount() == 0) throw new IllegalStateException("Concurrent membership assignment changed; retry from fresh guild data");
    }

    /** Persists a guild assignment in order with any in-flight profile save. */
    public CompletableFuture<Void> updateGuildMembershipAsync(UUID uuid, String guildTag, String guildRole) {
        return updateGuildMembershipAsync(uuid, guildTag, guildRole, null);
    }

    public CompletableFuture<Void> updateGuildMembershipAsync(UUID uuid, String guildTag, String guildRole, String expectedTag) {
        Objects.requireNonNull(uuid, "uuid");

        Document update = new Document("$set", new Document("guildTag", guildTag).append("guildRole", guildRole)
                .append("guildMembershipRevision", UUID.randomUUID().toString()));
        if (expectedTag != null) update.put("_expectedGuildTag", expectedTag);
        CompletableFuture<Void> write = profileOperations.submit(uuid, () ->
                CompletableFuture.runAsync(() -> persistUserUpdateWithGuildLock(uuid, update), profileLoader)
                        .thenCompose(ignored -> updateMembershipCache(uuid, update.get("$set", Document.class))));
        pendingWrites.add(write);
        write.whenComplete((ignored, error) -> {
            pendingWrites.remove(write);
        });
        return write.copy();
    }

    private CompletableFuture<Void> updateMembershipCache(UUID uuid, Document persisted) {
        CompletableFuture<Void> handoff = new CompletableFuture<>();
        cacheHandoffs.put(handoff, () -> handoff.complete(null));
        handoff.whenComplete((ignored, error) -> cacheHandoffs.remove(handoff));
        if (closing) {
            handoff.complete(null); // Durable write succeeded; no cache work is needed during shutdown.
            return handoff;
        }
        if (!BukkitTaskScheduler.execute(plugin, () -> {
            if (!handoff.isDone() && !closing) {
                User cached = onlineUsers.get(uuid);
                if (cached != null) {
                    String tag = persisted.getString("guildTag");
                    var guild = tag == null ? null : plugin.getGuildManager().getGuildCached(tag);
                    String role = guild == null ? null : guild.roleOf(uuid);
                    cached.setGuildTag(role == null ? null : guild.getTag());
                    cached.setGuildRole(role);
                }
            }
            handoff.complete(null);
        })) handoff.complete(null);
        return handoff;
    }

    /** Seal this source session and persist its profile before a transfer; call on the server thread. */
    public CompletableFuture<Void> prepareTransferProfileAsync(Player player) {
        return prepareTransferProfileAsync(player, null);
    }

    public CompletableFuture<Void> prepareTransferProfileAsync(Player player, org.bukkit.event.player.PlayerMoveEvent initialSeamMove) {
        return prepareTransferProfileAsync(player, initialSeamMove, null);
    }

    public CompletableFuture<Void> prepareTransferProfileAsync(Player player,
            org.bukkit.event.player.PlayerMoveEvent initialSeamMove, UUID transferId) {
        if (plugin.isShutdownDraining()) return CompletableFuture.failedFuture(new IllegalStateException("Server is saving"));
        if (isArriving(player)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Previous sector arrival is still being finalized"));
        }
        if (plugin.isCombatTagged(player)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player is in combat"));
        }
        if (closing || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) {
            return CompletableFuture.failedFuture(new IllegalStateException("Inactive player session"));
        }
        User user = onlineUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.failedFuture(new IllegalStateException("Profile is not loaded"));
        UUID uuid = player.getUniqueId();
        final TransferSnapshotGuard.Seal seal;
        try { seal = transferGuard.begin(player, initialSeamMove, transferId); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        Document update = createUserUpdate(player, user);
        CompletableFuture<Void> write = queueProfileWrite(uuid,
                () -> persistUserUpdateWithGuildLock(uuid, update));
        pendingWrites.add(write);
        CompletableFuture<Void> sealedWrite = write.whenComplete((ignored, error) -> {
            pendingWrites.remove(write);
            if (error == null) seal.saved = true;
            else transferGuard.releaseUnpublished(seal);
        });
        return sealedWrite.copy();
    }

    public boolean isTransferPending(Player player) { return transferGuard.active(player); }

    /** Only call after the publisher positively confirmed that the snapshot was not published. */
    public void releaseUnpublishedTransfer(Player player) { transferGuard.finish(player); }

    public boolean abortSectorTransfer(Player player, UUID transferId) { return transferGuard.abort(player, transferId); }

    public CompletableFuture<Void> saveCombatStats(Player player, User user) {
        if (closing || onlineUsers.get(player.getUniqueId()) != user || isTransferPending(player))
            return CompletableFuture.failedFuture(new IllegalStateException("Profile unavailable or transfer pending"));
        UUID uuid = player.getUniqueId();
        Document update = new Document("$set", new Document("name", player.getName())
                .append("kills", user.getKills()).append("deaths", user.getDeaths())
                .append("rating", user.getRating()).append("killstreak", user.getKillstreak()));
        CompletableFuture<Void> write = queueProfileWrite(uuid, () -> persistUserUpdateWithGuildLock(uuid, update));
        pendingWrites.add(write);
        write.whenComplete((ignored, error) -> {
            pendingWrites.remove(write);
            if (error != null) plugin.getLogger().warning("Could not persist combat statistics for " + uuid
                    + ": " + error.getMessage());
        });
        return write.copy();
    }

    public CompletableFuture<Void> saveCombatBypass(UUID uuid, boolean enabled) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("UserManager is shutting down"));
        var write = queueProfileWrite(uuid, () -> persistUserUpdateWithGuildLock(uuid,
                new Document("$set", new Document("combatBypass", enabled))));
        pendingWrites.add(write);
        write.whenComplete((ignored, error) -> pendingWrites.remove(write));
        return write.copy();
    }

    private CompletableFuture<Void> queueProfileWrite(UUID uuid, Runnable operation) {
        return profileOperations.submit(uuid, () -> CompletableFuture.runAsync(operation, profileLoader));
    }

    private Document createUserUpdate(Player player, User user) {
        // --- DROP SETTINGS ---
        Document dropSettingsDoc = new Document();
        for (Map.Entry<Material, Boolean> entry : user.getDropSettings().entrySet()) {
            dropSettingsDoc.append(entry.getKey().name(), entry.getValue());
        }

        // --- MINED BLOCKS ---
        Document minedBlocksDoc = new Document();
        for (Map.Entry<Material, Integer> entry : user.getMinedBlocks().entrySet()) {
            minedBlocksDoc.append(entry.getKey().name(), entry.getValue());
        }

        return new Document("$set", new Document(
                        "name", player.getName())
                        .append("godMode", user.isGodMode())
                        .append("flying", user.isFlying())
                        .append("vanished", user.isVanished())
                        .append("walkSpeed", user.getWalkSpeed())
                        .append("flySpeed", user.getFlySpeed())
                        .append("dropSettings", dropSettingsDoc)
                        .append("cobblestoneDropEnabled", user.isCobblestoneDropEnabled())
                        .append("miningLevel", user.getMiningLevel())
                        .append("minedBlocks", minedBlocksDoc)
                        .append("miningExperience", user.getMiningExperience())
                        .append("kills", user.getKills())
                        .append("deaths", user.getDeaths())
                        .append("rating", user.getRating())
                        .append("killstreak", user.getKillstreak())
                        .append("playTimeSeconds", user.getPlayTimeSeconds()));
        // Guild fields belong to explicit membership writes, not a stale quit snapshot.
    }


    public User getUser(Player player) { return onlineUsers.get(player.getUniqueId()); }

    public void clearGuildMembership(String tag) {
        String normalizedTag = ai.aisector.guilds.GuildManager.normalizeTag(tag);
        if (normalizedTag == null) return;
        for (User user : onlineUsers.values()) {
            if (normalizedTag.equals(ai.aisector.guilds.GuildManager.normalizeTag(user.getGuildTag()))) {
                user.setGuildTag(null);
                user.setGuildRole(null);
            }
        }
    }

    public void close() {
        closing = true;
        cacheHandoffs.values().forEach(Runnable::run);
        try {
            CompletableFuture.allOf(pendingWrites.toArray(CompletableFuture[]::new))
                    .handle((ignored, error) -> null).get(35, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted draining queued profile writes.");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
            plugin.getLogger().severe("Could not drain queued profile writes: " + pendingWrites.size());
        }
        profileLoader.shutdown();
        try {
            if (!profileLoader.awaitTermination(35, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for profile persistence before MongoDB shutdown.");
                profileLoader.shutdownNow();
                if (!profileLoader.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("Profile persistence tasks remain active during shutdown; pending writes: "
                            + pendingWrites.size());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            profileLoader.shutdownNow();
            plugin.getLogger().severe("Profile persistence shutdown was interrupted; pending writes: "
                    + pendingWrites.size());
        }
        pendingLoads.forEach((uuid, load) -> load.future().completeExceptionally(
                new IllegalStateException("UserManager is shutting down")));
        pendingLoads.clear();
    }

    public void saveOnlineUsers() {
        for (UUID uuid : new ArrayList<>(onlineUsers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            User user = onlineUsers.get(uuid);
            if (player == null || user == null) {
                continue;
            }
            if (user.getSessionStartMillis() > 0L) {
                user.addPlayTime((System.currentTimeMillis() - user.getSessionStartMillis()) / 1_000L);
            }
            unloadUser(player);
        }
    }

    public CompletableFuture<Void> checkpoint() {
        var saves = new ArrayList<CompletableFuture<Void>>(pendingWrites);
        for (Player player : Bukkit.getOnlinePlayers()) {
            User user = getUser(player);
            if (user == null || isArriving(player) || isTransferPending(player)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Player data is in transit: " + player.getName()));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            User user = getUser(player);
            Document update = createUserUpdate(player, user);
            long started = user.getSessionStartMillis();
            if (started > 0L) update.get("$set", Document.class).put("playTimeSeconds",
                    user.getPlayTimeSeconds() + Math.max(0L, (System.currentTimeMillis() - started) / 1000L));
            UUID id = player.getUniqueId();
            saves.add(queueProfileWrite(id, () -> persistUserUpdateWithGuildLock(id, update)));
        }
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        List<Map<String, Object>> serializedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            serializedEffects.add(effectData);
        }
        return serializedEffects;
    }

    private String itemStackToBase64(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return "v2:" + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private String itemStackArrayToBase64(ItemStack[] items) {
        return "v2:" + Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }
}
