import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Console-only test fixture, generated into staging only and removed by the harness. */
public class PlayerSmokePlugin extends JavaPlugin implements Listener {
    private SectorPlugin sector;
    private Path output;
    private List<String> players;
    private Location generatorLocation;
    private int generatorRewardsSpawned;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private java.util.concurrent.CompletableFuture<Object> redisLua(String script, List<String> keys) {
        // aiSECTOR relocates Jedis; keep the test fixture independent of its private dependency package.
        java.util.function.Function<Object, Object> operation = connection -> {
            try { return connection.getClass().getMethod("eval", String.class, List.class, List.class)
                    .invoke(connection, script, keys, List.of()); }
            catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        };
        return sector.getRedisManager().executeAsync((java.util.function.Function) operation);
    }

    public static class RejectingExecutor extends java.util.concurrent.AbstractExecutorService {
        public void shutdown() { }
        public java.util.List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return false; }
        public void execute(Runnable task) { throw new java.util.concurrent.RejectedExecutionException("fixture backpressure"); }
    }

    private ai.aisector.generators.GeneratorManager generators() throws Exception {
        var field = SectorPlugin.class.getDeclaredField("generatorManager");
        field.setAccessible(true);
        return (ai.aisector.generators.GeneratorManager) field.get(sector);
    }

    private Document generatorState() throws Exception {
        var block = generatorLocation.getBlock();
        var rewardColumn = new Location(block.getWorld(), generatorLocation.getX(),
                (block.getWorld().getMinHeight() + block.getWorld().getMaxHeight()) / 2.0, generatorLocation.getZ());
        long rewards = block.getWorld().getNearbyEntities(rewardColumn, 3,
                        (block.getWorld().getMaxHeight() - block.getWorld().getMinHeight()) / 2.0, 3).stream()
                .filter(entity -> entity.getScoreboardTags().contains("rwsmoke-" + players.getFirst())
                        && entity instanceof org.bukkit.entity.Item item && item.getItemStack().hasItemMeta()
                        && "STONE".equals(item.getItemStack().getItemMeta().getPersistentDataContainer()
                        .get(ai.aisector.generators.GeneratorItems.genKey(sector), PersistentDataType.STRING))).count();
        return new Document("registered", generators().isGenerator(generatorLocation)).append("block", block.getType().name())
                .append("rewards", generatorRewardsSpawned).append("loadedRewardEntities", rewards);
    }

    private int legacyMetadataCorpus() throws Exception {
        java.util.ArrayList<ItemStack> corpus = new java.util.ArrayList<>();
        ItemStack leather = new ItemStack(Material.LEATHER_CHESTPLATE);
        var leatherMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) leather.getItemMeta();
        leatherMeta.setColor(org.bukkit.Color.fromRGB(12, 145, 220));
        leather.setItemMeta(leatherMeta); corpus.add(leather);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        var skullMeta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        skullMeta.setOwnerProfile(Bukkit.createPlayerProfile(UUID.randomUUID(), "RwFixture"));
        head.setItemMeta(skullMeta); corpus.add(head);
        ItemStack potion = new ItemStack(Material.POTION);
        var potionMeta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        potionMeta.setColor(org.bukkit.Color.GREEN);
        potionMeta.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 200, 1), true);
        potion.setItemMeta(potionMeta); corpus.add(potion);
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        var fireworkMeta = (org.bukkit.inventory.meta.FireworkMeta) rocket.getItemMeta();
        fireworkMeta.setPower(2);
        fireworkMeta.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.RED)
                .withFade(org.bukkit.Color.BLUE).withTrail().build());
        rocket.setItemMeta(fireworkMeta); corpus.add(rocket);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        var bookMeta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        bookMeta.setTitle("Rewrite fixture"); bookMeta.setAuthor("RwFixture"); bookMeta.addPage("Unicode ąę test");
        book.setItemMeta(bookMeta); corpus.add(book);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        var swordMeta = sword.getItemMeta();
        swordMeta.getPersistentDataContainer().set(new NamespacedKey(this, "legacy_fixture"), PersistentDataType.STRING, "metadata");
        sword.setItemMeta(swordMeta); corpus.add(sword);
        for (ItemStack item : corpus) {
            try {
                var bytes = new java.io.ByteArrayOutputStream();
                try (var stream = new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) { stream.writeObject(item); }
                ItemStack restored = ai.aisector.utils.InventorySerializer.itemStackFromBase64(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
                if (!restored.isSimilar(item)) throw new AssertionError("Metadata differs after decode");
            } catch (Throwable error) { throw new IllegalStateException("Legacy metadata " + item.getType() + ": " + error, error); }
        }
        return corpus.size();
    }

    @Override public void onEnable() {
        sector = (SectorPlugin) getServer().getPluginManager().getPlugin("aiSECTOR");
        if (sector == null || !"aisector_staging".equals(sector.getConfig().getString("mongodb.database"))
                || sector.getConfig().getInt("redis.port") != 6380)
            throw new IllegalStateException("Test fixture is restricted to staging");
        output = Path.of(System.getProperty("aisector.smoke.output"));
        players = List.of(System.getProperty("aisector.smoke.players").split(","));
        if (players.size() != 2 || players.stream().anyMatch(p -> !p.matches("RwS[A-Za-z0-9]{4,13}")))
            throw new IllegalStateException("Invalid fixture players");
        Bukkit.getPluginManager().registerEvents(this, this);
        var bounds = sector.getSectorManager().getSectorByName(sector.getConfig().getString("this-sector-name"));
        int fixtureHash = players.getFirst().hashCode();
        generatorLocation = new Location(Bukkit.getWorlds().getFirst(),
                Math.floor((bounds.getMinX() + bounds.getMaxX()) / 2.0) + Math.floorMod(fixtureHash, 101) - 50,
                200, 80 + ((fixtureHash >>> 7) & 63));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player.getName())) return;
        // The fixture is not a survival test; avoid unrelated mob deaths while another client starts.
        player.setGameMode(GameMode.CREATIVE);
        player.setCanPickupItems(false); // Do not mistake old staging death drops for transfer duplication.
        player.setExpCooldown(Integer.MAX_VALUE); // The XP assertion excludes unrelated old staging experience orbs.
        for (String permission : List.of("aisector.setspawn", "aisector.command.setspawn", "aisector.command.tpa",
                "aisector.command.tp", "aisector.command.s", "aisector.command.sethome", "aisector.command.home",
                "aisector.command.vanish", "aisector.command.backup", "aisector.command.invsee", "aisector.command.tppos")) {
            player.addAttachment(this, permission, true);
        }
        player.addAttachment(this, "aisector.command.vanish.see", player.getName().endsWith("A"));
    }

    @EventHandler public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player.getName())) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            player.setCanPickupItems(false);
            player.setExpCooldown(Integer.MAX_VALUE);
        });
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        var loc = event.getLocation();
        var item = event.getEntity().getItemStack();
        if (generatorLocation != null && loc.getWorld().equals(generatorLocation.getWorld())
                && Math.abs(loc.getX() - generatorLocation.getX()) <= 3
                && Math.abs(loc.getZ() - generatorLocation.getZ()) <= 3 && loc.getY() >= 198
                && item.hasItemMeta() && "STONE".equals(item.getItemMeta().getPersistentDataContainer()
                .get(ai.aisector.generators.GeneratorItems.genKey(sector), PersistentDataType.STRING))) {
            event.getEntity().addScoreboardTag("rwsmoke-" + players.getFirst());
            generatorRewardsSpawned++;
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        if (args.length < 3 || !args[0].matches("[a-f0-9]+") || !players.contains(args[2])) return true;
        String request = args[0], action = args[1];
        Player player = Bukkit.getPlayerExact(args[2]);
        try {
            if (action.equals("legacy-corpus")) {
                reply(request, new Document("metadataCases", legacyMetadataCorpus()));
                return true;
            }
            if (action.equals("generator-register")) {
                if (!generatorLocation.getBlock().getType().isAir()) throw new IllegalStateException("Fixture cell is occupied; refusing overwrite");
                generatorLocation.getBlock().setType(Material.STONE);
                generators().register(generatorLocation, ai.aisector.generators.GeneratorType.STONE);
                reply(request, generatorState());
                return true;
            } else if (action.equals("generator-state")) {
                reply(request, generatorState());
                return true;
            }
            if (action.equals("state")) {
                reply(request, player == null ? new Document("online", false) : state(player));
                return true;
            }
            if (player == null) throw new IllegalStateException("Player not on this backend");
            if (action.equals("steady")) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFallDistance(0);
                reply(request, state(player));
            } else if (action.equals("profile-flags")) {
                var user = sector.getUserManager().getUser(player);
                user.setFlying(Boolean.parseBoolean(args[3]));
                user.setGodMode(Boolean.parseBoolean(args[4]));
                reply(request, state(player));
            } else if (action.equals("profile-transfer-reject")) {
                var manager = sector.getUserManager();
                var field = ai.aisector.user.UserManager.class.getDeclaredField("profileLoader");
                field.setAccessible(true);
                Object original = field.get(manager);
                java.util.concurrent.CompletableFuture<Void> rejected;
                try {
                    field.set(manager, new RejectingExecutor());
                    rejected = manager.prepareTransferProfileAsync(player);
                } finally { field.set(manager, original); }
                rejected.whenComplete((ignored, error) -> BukkitTaskScheduler.execute(this, () ->
                        reply(request, new Document("rejected", error != null)
                                .append("frozen", manager.isTransferPending(player)))));
            } else if (action.equals("seed")) {
                player.setGameMode(GameMode.CREATIVE);
                player.getInventory().clear();
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                var meta = sword.getItemMeta();
                meta.setDisplayName("Rewrite smoke sword");
                meta.setLore(List.of("metadata roundtrip", "26.3"));
                meta.getPersistentDataContainer().set(new NamespacedKey(this, "fixture"), PersistentDataType.STRING, "smoke");
                sword.setItemMeta(meta);
                player.getInventory().setItem(0, sword);
                player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, args.length > 3 ? Integer.parseInt(args[3]) : 37));
                player.getInventory().setItem(8, new ItemStack(Material.OBSIDIAN, 5));
                player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
                player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                player.getInventory().setHeldItemSlot(1);
                player.setLevel(7);
                player.setExp(.42f);
                player.setTotalExperience(117);
                player.setFoodLevel(17);
                player.setSaturation(5f);
                reply(request, state(player));
            } else if (action.equals("transfer")) {
                long started = System.nanoTime();
                var mutationsBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);
                if (args.length > 4 && args[4].equals("delayed-profile")) {
                    var field = ai.aisector.user.UserManager.class.getDeclaredField("profileOperations");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var queue = (ai.aisector.user.PerKeyAsyncQueue<UUID>) field.get(sector.getUserManager());
                    var gate = new java.util.concurrent.CompletableFuture<Void>();
                    queue.submit(player.getUniqueId(), () -> gate);
                    Bukkit.getScheduler().runTaskLater(this, () -> gate.complete(null), 40L);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        var item = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.DIRT));
                        try {
                            var drop = new org.bukkit.event.player.PlayerDropItemEvent(player, item);
                            Bukkit.getPluginManager().callEvent(drop);
                            var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                                    org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
                            Bukkit.getPluginManager().callEvent(click);
                            var commandEvent = new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/god");
                            Bukkit.getPluginManager().callEvent(commandEvent);
                            mutationsBlocked.set(drop.isCancelled() && click.isCancelled() && commandEvent.isCancelled()
                                    && sector.getUserManager().isTransferPending(player));
                        } finally { item.remove(); }
                    }, 10L);
                }
                String target = args[3];
                var destination = sector.getSectorManager().getSectorByName(target);
                if (destination == null) throw new IllegalArgumentException("Unknown target");
                double x = (destination.getMinX() + destination.getMaxX()) / 2.0;
                double z = (destination.getMinZ() + destination.getMaxZ()) / 2.0;
                var targetJson = new Document("world", player.getWorld().getName()).append("x", x).append("y", 100.0)
                        .append("z", z).append("yaw", 0).append("pitch", 0).toJson();
                String snapshot = sector.getUserManager().serializePlayerDataForTransfer(player, player.getLocation());
                sector.getSectorManager().transferPlayerAsync(player, target, snapshot,
                        java.util.Map.of("player:final_teleport_target:" + player.getUniqueId(), targetJson))
                        .whenComplete((v, error) -> reply(request, error == null ? new Document("published", true)
                                .append("elapsedMillis", (System.nanoTime() - started) / 1_000_000)
                                .append("mutationsBlocked", mutationsBlocked.get())
                                : new Document("error", error.toString())));
            } else if (action.equals("command")) {
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                player.performCommand(text);
                reply(request, new Document("executed", text));
            } else if (action.equals("kick")) {
                player.kickPlayer("Local reconnect smoke");
                reply(request, new Document("kicked", true));
            } else if (action.equals("inventory-clear")) {
                player.getInventory().clear();
                reply(request, state(player));
            } else if (action.equals("respawn")) {
                player.spigot().respawn();
                reply(request, new Document("requested", true));
            } else if (action.equals("die")) {
                player.setHealth(0);
                reply(request, new Document("dead", true));
            } else if (action.equals("gui")) {
                reply(request, new Document("title", player.getOpenInventory().getTitle())
                        .append("size", player.getOpenInventory().getTopInventory().getSize()));
            } else if (action.equals("homes")) {
                sector.getMongoDBManager().findManyAsync("player_homes", new Document("player_uuid", player.getUniqueId().toString()))
                        .whenComplete((homes, error) -> reply(request, error == null ? new Document("homes", homes)
                                : new Document("error", error.toString())));
            } else if (action.equals("home-recreate")) {
                UUID owner = player.getUniqueId();
                var repository = new ai.aisector.user.HomeSlotRepository(sector.getMongoDBManager());
                sector.getMongoDBManager().findOneAsync("player_homes", new Document("player_uuid", owner.toString()).append("home_slot", 1))
                        .thenCompose(home -> repository.deleteAsync(owner, 1).thenCompose(ignored -> repository.createAsync(owner, home)))
                        .whenComplete((ignored, error) -> reply(request, error == null ? new Document("recreated", true)
                                : new Document("error", error.toString())));
            } else if (action.equals("pending-placeholder")) {
                String victim = args[3];
                if (!players.contains(victim)) throw new IllegalArgumentException("Not a fixture victim");
                UUID victimId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + victim).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                redisLua("if redis.call('exists', KEYS[1]) == 0 then redis.call('set', KEYS[1], '{}', 'EX', 120); "
                                + "return 'OK' else return false end", List.of("player:pending_backup:" + victimId))
                        .whenComplete((status, error) -> reply(request, error == null ? new Document("status", status)
                                : new Document("error", error.toString())));
            } else if (action.equals("pending-clear-placeholder")) {
                String victim = args[3];
                if (!players.contains(victim)) throw new IllegalArgumentException("Not a fixture victim");
                UUID victimId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + victim).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                redisLua("if redis.call('get', KEYS[1]) == '{}' then return redis.call('del', KEYS[1]) else return 0 end",
                                List.of("player:pending_backup:" + victimId))
                        .whenComplete((removed, error) -> reply(request, error == null ? new Document("removed", removed)
                                : new Document("error", error.toString())));
            } else if (action.equals("backup-status")) {
                String victim = args[3];
                if (!players.contains(victim)) throw new IllegalArgumentException("Not a fixture victim");
                sector.getMongoDBManager().findManyAsync("death_backups", new Document("victim_name", victim),
                                new Document("timestamp", -1), 1)
                        .whenComplete((records, error) -> reply(request, error == null ? new Document("records", records)
                                : new Document("error", error.toString())));
            } else if (action.equals("permission-see")) {
                player.addAttachment(this, "aisector.command.vanish.see", Boolean.parseBoolean(args[3]));
                reply(request, state(player));
            } else if (action.equals("deny-admin-spawn-permission")) {
                player.addAttachment(this, "aisector.setspawn", false);
                reply(request, state(player));
            } else if (action.equals("pvp")) {
                if (!players.contains(args[3])) throw new IllegalArgumentException("Not a fixture target");
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) throw new IllegalArgumentException("Target is on another backend");
                GameMode actorMode = player.getGameMode(), targetMode = target.getGameMode();
                double originalHealth = target.getHealth();
                var user = sector.getUserManager().getUser(player);
                String originalTag = user.getGuildTag(), originalRole = user.getGuildRole();
                try {
                    if (args.length > 4 && args[4].equals("missing-guild")) {
                        user.setGuildTag("NO_SUCH_FIXTURE");
                        user.setGuildRole("MEMBER");
                    }
                    player.setGameMode(GameMode.SURVIVAL);
                    target.setGameMode(GameMode.SURVIVAL);
                    target.setInvulnerable(false);
                    target.setHealth(20);
                    target.setNoDamageTicks(0);
                    target.damage(4, player);
                    reply(request, new Document("healthAfter", target.getHealth()));
                } finally {
                    user.setGuildTag(originalTag);
                    user.setGuildRole(originalRole);
                    target.setHealth(originalHealth);
                    player.setGameMode(actorMode);
                    target.setGameMode(targetMode);
                }
            } else if (action.equals("projectile-pvp")) {
                if (!players.contains(args[3])) throw new IllegalArgumentException("Not a fixture target");
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) throw new IllegalArgumentException("Target is on another backend");
                var user = sector.getUserManager().getUser(player);
                String tag = user.getGuildTag(), role = user.getGuildRole();
                var arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
                try {
                    if (args.length > 4 && args[4].equals("missing-guild")) {
                        user.setGuildTag("NO_SUCH_FIXTURE"); user.setGuildRole("MEMBER");
                    }
                    var event = new org.bukkit.event.entity.EntityDamageByEntityEvent(arrow, target,
                            org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE, 4);
                    Bukkit.getPluginManager().callEvent(event);
                    reply(request, new Document("cancelled", event.isCancelled()));
                } finally { arrow.remove(); user.setGuildTag(tag); user.setGuildRole(role); }
            } else if (action.equals("stale-owner")) {
                var user = sector.getUserManager().getUser(player);
                String previousTag = user.getGuildTag(), previousRole = user.getGuildRole();
                String tag = "RG" + UUID.randomUUID().toString().substring(0, 3).toUpperCase(java.util.Locale.ROOT);
                var guild = new ai.aisector.guilds.Guild(tag, "role fixture", UUID.randomUUID());
                guild.getMembers().add(guild.getOwner());
                guild.getMembers().add(player.getUniqueId());
                sector.getGuildManager().createGuildAsync(guild).whenComplete((created, createError) -> {
                    if (createError != null || created == null) {
                        reply(request, new Document("error", "Cannot create isolated role fixture"));
                        return;
                    }
                    BukkitTaskScheduler.execute(this, () -> {
                        user.setGuildTag(tag);
                        user.setGuildRole("OWNER"); // Deliberately stale profile role; guild says MEMBER.
                        player.performCommand("gildia usun");
                        boolean protectedGuild = sector.getGuildManager().getGuildCached(tag) == created
                                && "MEMBER".equals(user.getGuildRole());
                        user.setGuildTag(previousTag);
                        user.setGuildRole(previousRole);
                        sector.getGuildManager().deleteGuildAsync(created).whenComplete((deleted, error) ->
                                reply(request, error == null ? new Document("protected", protectedGuild).append("cleaned", deleted)
                                        : new Document("error", error.toString())));
                    });
                });
            } else if (action.equals("generator-reject")) {
                var manager = generators();
                var field = ai.aisector.generators.GeneratorManager.class.getDeclaredField("mongoWriter");
                field.setAccessible(true);
                Object original = field.get(manager);
                java.util.concurrent.CompletableFuture<Boolean> rejected;
                try {
                    field.set(manager, new RejectingExecutor());
                    rejected = manager.unregisterAsync(generatorLocation);
                } finally { field.set(manager, original); }
                rejected.whenComplete((removed, error) -> {
                    try { reply(request, generatorState().append("rejected", error != null)); }
                    catch (Exception failure) { reply(request, new Document("error", failure.toString())); }
                });
            } else if (action.equals("generator-break")) {
                player.getInventory().setItemInMainHand(new ItemStack(args[3].equals("gold")
                        ? Material.GOLDEN_PICKAXE : Material.DIAMOND_PICKAXE));
                boolean broken = player.breakBlock(generatorLocation.getBlock());
                reply(request, generatorState().append("breakReturned", broken));
            } else if (action.equals("generator-cancelled-break")) {
                var event = new org.bukkit.event.block.BlockBreakEvent(generatorLocation.getBlock(), player);
                event.setCancelled(true);
                Bukkit.getPluginManager().callEvent(event);
                reply(request, generatorState());
            } else if (action.equals("click")) {
                int slot = Integer.parseInt(args[3]);
                var event = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, slot,
                        args.length > 4 && args[4].equals("left") ? org.bukkit.event.inventory.ClickType.LEFT
                                : org.bukkit.event.inventory.ClickType.RIGHT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
                Bukkit.getPluginManager().callEvent(event);
                reply(request, new Document("cancelled", event.isCancelled()));
            } else throw new IllegalArgumentException(action);
        } catch (Throwable error) { reply(request, new Document("error", error.toString())); }
        return true;
    }

    private Document state(Player player) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        int slot = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            digest.update((byte) slot++);
            if (item != null && !item.isEmpty()) {
                digest.update(item.serializeAsBytes());
                items.add((slot - 1) + ":" + item.getType() + ":" + item.getAmount());
            }
        }
        var user = sector.getUserManager().getUser(player);
        var loc = player.getLocation();
        return new Document("online", player.isOnline()).append("name", player.getName()).append("uuid", player.getUniqueId().toString())
                .append("profileFlying", user != null && user.isFlying()).append("profileGodMode", user != null && user.isGodMode())
                .append("session", System.identityHashCode(player))
                .append("op", player.isOp()).append("spawnAdminPermission", player.hasPermission("aisector.setspawn"))
                .append("dead", player.isDead())
                .append("ready", user != null && player.getWalkSpeed() > 0).append("walkSpeed", player.getWalkSpeed())
                .append("invulnerable", player.isInvulnerable()).append("inventoryHash", HexFormat.of().formatHex(digest.digest()))
                .append("items", items).append("held", player.getInventory().getHeldItemSlot()).append("level", player.getLevel())
                .append("exp", player.getExp()).append("totalExperience", player.getTotalExperience()).append("food", player.getFoodLevel())
                .append("gameMode", player.getGameMode().name()).append("world", player.getWorld().getName())
                .append("vanishTags", player.getScoreboard().getTeam("z_vanished") == null ? List.of()
                        : new java.util.ArrayList<>(player.getScoreboard().getTeam("z_vanished").getEntries()))
                .append("x", loc.getX()).append("y", loc.getY()).append("z", loc.getZ())
                .append("visibleOther", Bukkit.getOnlinePlayers().stream().filter(p -> p != player).allMatch(player::canSee));
    }

    private void reply(String request, Document document) {
        try { Files.writeString(output.resolve(getServer().getPort() + "-" + request + ".json"), document.toJson()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
}
