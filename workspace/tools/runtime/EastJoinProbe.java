import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EastJoinProbe {
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        String[] fields = args.split("\\|", 3);
        if (!fields[0].matches("RwSEast[a-f0-9]{6}")) throw new IllegalArgumentException("Not a test account");
        Class<?> api = java.util.Arrays.stream(instrumentation.getAllLoadedClasses())
                .filter(type -> type.getName().equals("org.bukkit.Bukkit")).findFirst().orElseThrow();
        var loader = api.getClassLoader();
        var plugin = loader.loadClass("org.bukkit.plugin.Plugin");
        var manager = loader.loadClass("org.bukkit.plugin.PluginManager");
        var scheduler = loader.loadClass("org.bukkit.scheduler.BukkitScheduler");
        var player = loader.loadClass("org.bukkit.entity.Player");
        var entity = loader.loadClass("org.bukkit.entity.Entity");
        var location = loader.loadClass("org.bukkit.Location");
        var stack = loader.loadClass("org.bukkit.inventory.ItemStack");
        var material = loader.loadClass("org.bukkit.Material");
        var inventory = loader.loadClass("org.bukkit.inventory.PlayerInventory");
        var living = loader.loadClass("org.bukkit.entity.LivingEntity");
        Object owner = manager.getMethod("getPlugin", String.class).invoke(api.getMethod("getPluginManager").invoke(null), "aiSECTOR");
        scheduler.getMethod("runTask", plugin, Runnable.class).invoke(api.getMethod("getScheduler").invoke(null), owner, (Runnable) () -> {
            try {
                Object gracz = api.getMethod("getPlayerExact", String.class).invoke(null, fields[0]);
                if (gracz == null) { Files.writeString(Path.of(fields[1]), "offline"); return; }
                String action = fields.length == 3 ? fields[2] : "";
                if (java.util.Set.of("grant-tppos", "grant-god", "grant-fly").contains(action)) {
                    String permission = "aisector.command." + action.substring("grant-".length());
                    loader.loadClass("org.bukkit.permissions.Permissible")
                            .getMethod("addAttachment", plugin, String.class, boolean.class)
                            .invoke(gracz, owner, permission, true);
                    Files.writeString(Path.of(fields[1]), "OK");
                    return;
                }
                if (action.equals("seed") || action.equals("inventory")) {
                    Object inv = player.getMethod("getInventory").invoke(gracz);
                    if (action.equals("seed")) {
                        inventory.getMethod("clear").invoke(inv);
                        living.getMethod("setCanPickupItems", boolean.class).invoke(gracz, false);
                        inventory.getMethod("setItem", int.class, stack).invoke(inv, 4,
                                stack.getConstructor(material, int.class).newInstance(material.getField("DIAMOND").get(null), 19));
                        inventory.getMethod("setHelmet", stack).invoke(inv,
                                stack.getConstructor(material).newInstance(material.getField("IRON_HELMET").get(null)));
                        inventory.getMethod("setItemInOffHand", stack).invoke(inv,
                                stack.getConstructor(material).newInstance(material.getField("SHIELD").get(null)));
                        player.getMethod("setLevel", int.class).invoke(gracz, 7);
                        player.getMethod("setExp", float.class).invoke(gracz, .42f);
                    }
                    Object contents = inventory.getMethod("getContents").invoke(inv);
                    byte[] bytes = (byte[]) stack.getMethod("serializeItemsAsBytes", contents.getClass()).invoke(null, contents);
                    var slots = new java.util.ArrayList<String>();
                    for (int i = 0; i < java.lang.reflect.Array.getLength(contents); i++) {
                        Object item = java.lang.reflect.Array.get(contents, i);
                        if (item != null && !Boolean.TRUE.equals(stack.getMethod("isEmpty").invoke(item)))
                            slots.add(i + ":" + stack.getMethod("getType").invoke(item) + ":" + stack.getMethod("getAmount").invoke(item));
                    }
                    Files.writeString(Path.of(fields[1]), java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
                            + " " + player.getMethod("getLevel").invoke(gracz) + " " + player.getMethod("getExp").invoke(gracz)
                            + " " + String.join(",", slots));
                    return;
                }
                Object loc = entity.getMethod("getLocation").invoke(gracz);
                if (action.equals("spawn-button")) {
                    if ((int) api.getMethod("getPort").invoke(null) != 25566)
                        throw new IllegalStateException("Not the test Spawn");
                    location.getMethod("setX", double.class).invoke(loc, 2050.5);
                    location.getMethod("setY", double.class).invoke(loc, 126.0);
                    location.getMethod("setZ", double.class).invoke(loc, 2048.5);
                } else if (action.equals("flight-start")) {
                    boolean east = (int) api.getMethod("getPort").invoke(null) == 25568;
                    location.getMethod("setX", double.class).invoke(loc, east ? 32.5 : -32.5);
                    location.getMethod("setY", double.class).invoke(loc, 170.0);
                    location.getMethod("setZ", double.class).invoke(loc, 32.5);
                } else if (action.equals("seam")) {
                    boolean east = (int) api.getMethod("getPort").invoke(null) == 25568;
                    Object world = location.getMethod("getWorld").invoke(loc);
                    var worldApi = loader.loadClass("org.bukkit.World");
                    var blockApi = loader.loadClass("org.bukkit.block.Block");
                    var height = worldApi.getMethod("getHighestBlockYAt", int.class, int.class);
                    var blockAt = worldApi.getMethod("getBlockAt", int.class, int.class, int.class);
                    int ground = -1, z = 0;
                    for (int candidate = 16; candidate < 64; candidate++) {
                        int left = (int) height.invoke(world, -1, candidate);
                        int right = (int) height.invoke(world, 0, candidate);
                        if (left != right) continue;
                        Object leftBlock = blockAt.invoke(world, -1, left, candidate);
                        Object rightBlock = blockAt.invoke(world, 0, right, candidate);
                        boolean good = true;
                        for (Object block : new Object[]{leftBlock, rightBlock}) {
                            String type = blockApi.getMethod("getType").invoke(block).toString();
                            if (!java.util.Set.of("GRASS_BLOCK", "DIRT", "STONE", "SAND").contains(type)) good = false;
                        }
                        if (good) { ground = left; z = candidate; break; }
                    }
                    if (ground < 0) throw new IllegalStateException("No flat seam column");
                    location.getMethod("setX", double.class).invoke(loc, east ? .3 : -.3);
                    location.getMethod("setY", double.class).invoke(loc, ground + 1.0);
                    location.getMethod("setZ", double.class).invoke(loc, z + .5);
                }
                if (java.util.Set.of("seam", "spawn-button", "flight-start").contains(action)) {
                    var applied = (java.util.concurrent.CompletableFuture<?>) entity.getMethod("teleportAsync", location).invoke(gracz, loc);
                    applied.whenComplete((ok, error) -> {
                        try { Files.writeString(Path.of(fields[1]), Boolean.TRUE.equals(ok) ? "OK" : "ERROR " + error); }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                    });
                    return;
                }
                Files.writeString(Path.of(fields[1]), location.getMethod("getX").invoke(loc) + " "
                        + location.getMethod("getY").invoke(loc) + " " + location.getMethod("getZ").invoke(loc) + " "
                        + player.getMethod("getWalkSpeed").invoke(gracz));
            } catch (Exception error) { throw new IllegalStateException(error); }
        });
    }
}
