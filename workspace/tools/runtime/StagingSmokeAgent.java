import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Test-only attach agent. Never installed as a plugin; used only by staging_smoke.py. */
public class StagingSmokeAgent {
    private static Object rememberedGuild;
    public static final class InspectStream extends java.io.ObjectInputStream {
        private final ClassLoader loader;
        InspectStream(byte[] bytes, ClassLoader loader) throws java.io.IOException {
            super(new java.io.ByteArrayInputStream(bytes));
            this.loader = loader;
        }
        @Override protected Class<?> resolveClass(java.io.ObjectStreamClass descriptor)
                throws java.io.IOException, ClassNotFoundException {
            return Class.forName(descriptor.getName(), false, loader);
        }
    }
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        String[] fields = args.split("\\|", 3);
        String action = fields[0], tag = fields[1];
        Path output = Path.of(fields[2]);
        Class<?> bukkit = null;
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals("org.bukkit.Bukkit")) { bukkit = loaded; break; }
        }
        if (bukkit == null) throw new IllegalStateException("Bukkit unavailable");
        ClassLoader loader = bukkit.getClassLoader();
        Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, loader);
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        Object plugin = Class.forName("org.bukkit.plugin.PluginManager", false, loader)
                .getMethod("getPlugin", String.class).invoke(manager, "aiSECTOR");
        if (plugin == null) throw new IllegalStateException("aiSECTOR unavailable");
        Object config = plugin.getClass().getMethod("getConfig").invoke(plugin);
        Class<?> configuration = Class.forName("org.bukkit.configuration.ConfigurationSection", false, loader);
        if (!"aisector_staging".equals(configuration.getMethod("getString", String.class)
                .invoke(config, "mongodb.database"))) throw new IllegalStateException("Not staging database");
        Runnable task = () -> {
            try {
                Object guilds = plugin.getClass().getMethod("getGuildManager").invoke(plugin);
                Class<?> guildType = Class.forName("ai.aisector.guilds.Guild", true, plugin.getClass().getClassLoader());
                CompletableFuture<?> result;
                if (action.equals("create")) {
                    Object guild = guildType.getConstructor(String.class, String.class, UUID.class)
                            .newInstance(tag, "runtime-smoke", UUID.randomUUID());
                    result = (CompletableFuture<?>) guilds.getClass().getMethod("createGuildAsync", guildType).invoke(guilds, guild);
                } else if (action.equals("load")) {
                    result = (CompletableFuture<?>) guilds.getClass().getMethod("reloadGuildAsync", String.class).invoke(guilds, tag);
                } else if (action.equals("remember")) {
                    rememberedGuild = guilds.getClass().getMethod("getGuild", String.class).invoke(guilds, tag);
                    result = CompletableFuture.completedFuture(rememberedGuild == null ? "MISSING" : "REMEMBERED");
                } else if (action.equals("saveRemembered")) {
                    result = (CompletableFuture<?>) guilds.getClass().getMethod("saveGuild", guildType)
                            .invoke(guilds, rememberedGuild);
                } else if (action.equals("probe")) {
                    Object guild = guilds.getClass().getMethod("getGuild", String.class).invoke(guilds, tag);
                    result = CompletableFuture.completedFuture(guild == null ? "MISSING" : "PRESENT");
                } else if (action.equals("inventory")) {
                    Class<?> material = Class.forName("org.bukkit.Material", true, loader);
                    Class<?> itemType = Class.forName("org.bukkit.inventory.ItemStack", true, loader);
                    Object item = itemType.getConstructor(material).newInstance(material.getField("DIAMOND_SWORD").get(null));
                    Object meta = itemType.getMethod("getItemMeta").invoke(item);
                    Class<?> itemMeta = Class.forName("org.bukkit.inventory.meta.ItemMeta", true, loader);
                    itemMeta.getMethod("setDisplayName", String.class).invoke(meta, "Smoke sword");
                    itemMeta.getMethod("setLore", java.util.List.class).invoke(meta, java.util.List.of("line 1", "line 2"));
                    itemType.getMethod("setItemMeta", itemMeta).invoke(item, meta);
                    Class<?> serializer = Class.forName("ai.aisector.utils.InventorySerializer", true, plugin.getClass().getClassLoader());
                    String encoded = (String) serializer.getMethod("itemStackToBase64", itemType).invoke(null, item);
                    Object restored = serializer.getMethod("itemStackFromBase64", String.class).invoke(null, encoded);
                    if (!(Boolean) itemType.getMethod("isSimilar", itemType).invoke(item, restored))
                        throw new AssertionError("Inventory roundtrip failed");
                    // Also exercise an actual legacy BukkitObjectOutputStream fixture on this Paper version.
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    Class<?> legacyOutput = Class.forName("org.bukkit.util.io.BukkitObjectOutputStream", true, loader);
                    java.io.ObjectOutputStream stream = (java.io.ObjectOutputStream) legacyOutput
                            .getConstructor(java.io.OutputStream.class).newInstance(bytes);
                    stream.writeObject(item);
                    stream.close();
                    String legacy = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
                    java.util.Set<String> legacyClasses = new java.util.TreeSet<>();
                    try (java.io.ObjectInputStream input = new InspectStream(bytes.toByteArray(), loader)) {
                        input.setObjectInputFilter(info -> {
                            if (info.serialClass() != null) legacyClasses.add(info.serialClass().getName());
                            return java.io.ObjectInputFilter.Status.UNDECIDED;
                        });
                        input.readObject();
                    }
                    Files.writeString(output.resolveSibling("legacy-item-stream-classes.json"), legacyClasses.toString());
                    Object legacyItem = serializer.getMethod("itemStackFromBase64", String.class).invoke(null, legacy);
                    if (!(Boolean) itemType.getMethod("isSimilar", itemType).invoke(item, legacyItem))
                        throw new AssertionError("Legacy inventory roundtrip failed");
                    int checkedItems = 0;
                    for (Object entry : material.getEnumConstants()) {
                        if (!(Boolean) material.getMethod("isItem").invoke(entry)
                                || (Boolean) material.getMethod("isAir").invoke(entry)
                                || (Boolean) material.getMethod("isLegacy").invoke(entry)) continue;
                        try {
                            Object candidate = itemType.getConstructor(material).newInstance(entry);
                            java.io.ByteArrayOutputStream itemBytes = new java.io.ByteArrayOutputStream();
                            try (java.io.ObjectOutputStream itemStream = (java.io.ObjectOutputStream) legacyOutput
                                    .getConstructor(java.io.OutputStream.class).newInstance(itemBytes)) {
                                itemStream.writeObject(candidate);
                            }
                            Object decoded = serializer.getMethod("itemStackFromBase64", String.class).invoke(null,
                                    java.util.Base64.getEncoder().encodeToString(itemBytes.toByteArray()));
                            if (!(Boolean) itemType.getMethod("isSimilar", itemType).invoke(candidate, decoded))
                                throw new AssertionError("Item differs after decode");
                            checkedItems++;
                        } catch (Throwable error) {
                            throw new IllegalStateException("Legacy corpus failed for " + entry + ": "
                                    + (error.getCause() == null ? error : error.getCause()), error);
                        }
                    }
                    Files.writeString(output.resolveSibling("legacy-item-corpus.result"), "ITEMS_OK:" + checkedItems);
                    result = CompletableFuture.completedFuture("INVENTORY_ROUNDTRIP_OK");
                } else if (action.equals("profile")) {
                    UUID playerId = UUID.randomUUID();
                    Object users = plugin.getClass().getMethod("getUserManager").invoke(plugin);
                    Class<?> userType = Class.forName("ai.aisector.user.User", true, plugin.getClass().getClassLoader());
                    Object user = userType.getConstructor(UUID.class).newInstance(playerId);
                    userType.getMethod("setGuildTag", String.class).invoke(user, "INVALID_SMOKE");
                    java.lang.reflect.Field cacheField = users.getClass().getDeclaredField("onlineUsers");
                    cacheField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<UUID, Object> cache = (java.util.Map<UUID, Object>) cacheField.get(users);
                    cache.put(playerId, user);
                    CompletableFuture<?> write = (CompletableFuture<?>) users.getClass()
                            .getMethod("updateGuildMembershipAsync", UUID.class, String.class, String.class)
                            .invoke(users, playerId, "NO" + playerId.toString().substring(0, 8), "OWNER");
                    result = write.thenApply(ignored -> {
                        try {
                            if (userType.getMethod("getGuildTag").invoke(user) != null
                                    || userType.getMethod("getGuildRole").invoke(user) != null)
                                throw new AssertionError("Cache did not match rejected Mongo membership");
                            return "PROFILE_CACHE_VALIDATION_OK";
                        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
                        finally { cache.remove(playerId); }
                    });
                    result = result.thenCompose(value -> {
                        try {
                            Object mongo = plugin.getClass().getMethod("getMongoDBManager").invoke(plugin);
                            Class<?> document = Class.forName("org.bson.Document", true, plugin.getClass().getClassLoader());
                            Object filter = document.getConstructor(String.class, Object.class).newInstance("uuid", playerId.toString());
                            CompletableFuture<?> cleanup = (CompletableFuture<?>) mongo.getClass().getMethod("deleteManyAsync", String.class,
                                    Class.forName("org.bson.conversions.Bson", true, plugin.getClass().getClassLoader()))
                                    .invoke(mongo, "users", filter);
                            return cleanup.thenApply(ignored -> value);
                        } catch (Exception cleanupError) { return CompletableFuture.failedFuture(cleanupError); }
                    });
                } else {
                    Object guild = guilds.getClass().getMethod("getGuild", String.class).invoke(guilds, tag);
                    if (guild == null) throw new IllegalStateException("Guild missing for " + action);
                    if (action.equals("save")) {
                        guildType.getMethod("setName", String.class).invoke(guild, "fresh-runtime");
                        result = (CompletableFuture<?>) guilds.getClass().getMethod("saveGuild", guildType).invoke(guilds, guild);
                    } else if (action.equals("delete")) {
                        result = (CompletableFuture<?>) guilds.getClass().getMethod("deleteGuildAsync", guildType).invoke(guilds, guild);
                    } else throw new IllegalArgumentException(action);
                }
                result.whenComplete((value, error) -> write(output, error == null ? "OK:" + value : "ERROR:" + error));
            } catch (Throwable error) { write(output, "ERROR:" + error); }
        };
        Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
        Class.forName("org.bukkit.scheduler.BukkitScheduler", false, loader)
                .getMethod("runTask", pluginType, Runnable.class).invoke(scheduler, plugin, task);
    }

    private static void write(Path path, String text) {
        try { Files.writeString(path, text); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
}
