package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.guilds.Guild;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.sectors.RandomSurfaceLanding;
import ai.aisector.user.User;
import ai.aisector.user.PlayerFlight;
import ai.aisector.user.UserManager;
import ai.aisector.utils.HotbarSlotSync;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.utils.InventorySerializer;
import ai.aisector.utils.MessageUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PlayerDataListener implements Listener {
    private record JoinNetworkState(String respawnMarker, String playerData, String finalTargetData,
                                    String forcedSector, String pendingBackup) {
        boolean respawning() {
            return respawnMarker != null;
        }
    }

    private static final String DELETE_IF_UNCHANGED_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] "
            + "then return redis.call('del', KEYS[1]) else return 0 end";

    private final SectorPlugin plugin;
    private final UserManager userManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();
    record PreparedLanding(String target, Location location) {
        boolean matches(String finalTarget) { return target.equals(finalTarget); }
    }
    private record RestoredTransfer(int heldSlot, PlayerFlight flight) {}
    private final Map<UUID, PreparedLanding> preparedLandings = new java.util.concurrent.ConcurrentHashMap<>();


    public PlayerDataListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.redisManager = plugin.getRedisManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConfigure(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Hold the joining player until async profile and transfer state are available.
        player.setInvulnerable(true);
        player.setWalkSpeed(0.0f);
        if (!preparedLandings.containsKey(player.getUniqueId())) player.setFlySpeed(0.0f);
        CompletableFuture<JoinNetworkState> network = loadNetworkState(player.getUniqueId());
        userManager.loadUserAsync(player).whenComplete((user, loadError) ->
                scheduleMainThread(() -> {
                    if (!isCurrentSession(player)) return;
                    if (loadError != null || user == null) {
                        plugin.getLogger().severe("Could not load profile for " + player.getName() + ": "
                                + (loadError == null ? "empty profile result" : loadError.getMessage()));
                        player.kickPlayer("§cWystąpił krytyczny błąd podczas ładowania Twojego profilu.");
                        return;
                    }
                    reconcileGuildMembershipAndConfigure(player, user, network);
                }));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void chooseTransferSpawn(AsyncPlayerSpawnLocationEvent event) {
        UUID uuid = event.getConnection().getProfile().getId();
        if (uuid == null || Bukkit.isPrimaryThread()) return;
        try {
            String target = redisManager.executeAsync(jedis -> jedis.get("player:final_teleport_target:" + uuid))
                    .get(300, TimeUnit.MILLISECONDS);
            if (target == null || target.length() > 1024) return;
            var json = gson.fromJson(target, com.google.gson.JsonObject.class);
            if (json == null) return;
            Location landing;
            if (isSurfaceTarget(json) && !sectorManager.isLockedSpawnBackend()) {
                json.addProperty("y", 0);
                Location seed = locationFromJson(json.toString());
                Sector sector = sectorManager.randomArrivalSector(seed);
                if (sector == null) return;
                landing = new RandomSurfaceLanding(plugin).prepare(seed, sector).get(1400, TimeUnit.MILLISECONDS);
            } else {
                landing = locationFromJson(target);
            }
            if (!validArrival(landing)) return;
            event.setSpawnLocation(landing);
            var prepared = new PreparedLanding(target, landing);
            preparedLandings.put(uuid, prepared);
            Bukkit.getScheduler().runTaskLater(plugin, () -> preparedLandings.remove(uuid, prepared), 600L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException unavailable) {
            // This is an optimization only. Join will load and validate the complete transfer normally.
        }
    }

    private static boolean isSurfaceTarget(com.google.gson.JsonObject target) {
        var surface = target.get("surface");
        return surface != null && surface.isJsonPrimitive() && surface.getAsJsonPrimitive().isBoolean()
                && surface.getAsBoolean();
    }

    private boolean validArrival(Location arrival) {
        if (arrival == null || arrival.getWorld() == null || arrival.getY() < arrival.getWorld().getMinHeight()
                || arrival.getY() >= arrival.getWorld().getMaxHeight()) return false;
        return sectorManager.isLockedSpawnBackend() ? sectorManager.insideLockedSpawn(arrival) : sectorManager.owns(arrival);
    }

    private void reconcileGuildMembershipAndConfigure(Player player, User user,
                                                       CompletableFuture<JoinNetworkState> network) {
        String guildTag = user.getGuildTag();
        if (guildTag == null || guildTag.isBlank()) {
            configureLoadedPlayer(player, user, network);
            return;
        }

        UUID playerUuid = player.getUniqueId();
        plugin.getGuildManager().reloadGuildAsync(guildTag).whenComplete((guild, error) ->
                scheduleMainThread(() -> {
                    if (!isCurrentSession(player)) return;
                    if (error != null) {
                        plugin.getLogger().warning("Could not reconcile guild membership for " + playerUuid + ": "
                                + error.getMessage());
                    } else if (guild == null || !guild.getMembers().contains(playerUuid)) {
                        user.setGuildTag(null);
                        user.setGuildRole(null);
                    } else if (playerUuid.equals(guild.getOwner())) {
                        user.setGuildRole("OWNER");
                    } else if (guild.getMods().contains(playerUuid)) {
                        user.setGuildRole("MOD");
                    } else {
                        user.setGuildRole("MEMBER");
                    }
                    configureLoadedPlayer(player, user, network);
                }));
    }

    private CompletableFuture<JoinNetworkState> loadNetworkState(UUID playerUuid) {
        return redisManager.executeAsync(jedis -> {
            var values = jedis.mget("player:is_respawning:" + playerUuid,
                    "player:data:" + playerUuid, "player:final_teleport_target:" + playerUuid,
                    "player:force_sector_spawn:" + playerUuid, "player:pending_backup:" + playerUuid);
            return new JoinNetworkState(values.get(0), values.get(1), values.get(2), values.get(3), values.get(4));
        });
    }

    private void configureLoadedPlayer(Player player, User user, CompletableFuture<JoinNetworkState> network) {
            // Persistent user settings and transfer state must only be applied after the async profile load.
            // Keep the join hold until network state and the asynchronous destination teleport finish.

            // 1) natychmiast lokalnie (żeby nie było “pustych tagów” po join)
            plugin.getGuildTagManager().applyLocalTagsNow(player);

            // 2) po chwili pełny update dla gracza (pakiet/redis)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isCurrentSession(player)) return;
                plugin.getGuildTagManager().updateTagsFor(player);
            }, 25L);

            // 3) jeśli gracz ma gildię -> odśwież widok wszystkim, którzy już są na serwerze
            User joined = user;
            if (joined != null && joined.hasGuild()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isCurrentSession(player)) return;
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (viewer.equals(player)) continue;
                        plugin.getGuildTagManager().updateTagsFor(viewer);
                    }
                }, 30L);
            }

            network.whenComplete((state, error) -> runOnMainThread(() -> {
                        if (!isCurrentSession(player)) return;
                        if (error != null) {
                            plugin.getLogger().severe("Failed to load join/transfer state for " + player.getName()
                                    + ": " + error.getMessage());
                            player.kickPlayer("§cNie udało się bezpiecznie wczytać danych sieciowych. Połącz się ponownie za chwilę.");
                            return;
                        }
                        try {
                            applyNetworkState(player, state);
                        } catch (RuntimeException exception) {
                            plugin.getLogger().severe("Failed to apply join/transfer state for " + player.getName()
                                    + ": " + exception.getMessage());
                            player.kickPlayer("§cNie udało się bezpiecznie wczytać danych sieciowych. Połącz się ponownie za chwilę.");
                        }
                    }));

    }

    private void scheduleMainThread(Runnable task) {
        BukkitTaskScheduler.execute(plugin, task);
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else scheduleMainThread(task);
    }

    private boolean isCurrentSession(Player player) {
        return player != null && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private void applyNetworkState(Player player, JoinNetworkState state) {
        UUID playerUuid = player.getUniqueId();
        PreparedLanding prepared = preparedLandings.remove(playerUuid);
        RestoredTransfer restored = null;
        if (state.playerData() != null) {
            try {
                restored = loadTransferData(player, state.playerData());
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Transfer snapshot for " + player.getName()
                        + " could not be restored; the Redis copy was retained: " + exception.getMessage());
                player.kickPlayer("§cNie udało się bezpiecznie przywrócić Twoich danych. Spróbuj ponownie za chwilę.");
                return;
            }
        }
        User transferUser = userManager.getUser(player);
        if (transferUser == null) throw new IllegalStateException("Profile cache disappeared during join");
        if (state.playerData() == null) PlayerFlight.resolve(player.getGameMode(), transferUser.isFlying(), null).apply(player);

        if (state.pendingBackup() != null) {
            if (state.pendingBackup().length() > 1_048_576) {
                throw new IllegalArgumentException("Pending backup exceeds the 1 MiB limit");
            }
            InventorySerializer.deserializeAndUpdateInventory(player.getInventory(), state.pendingBackup());
            player.sendMessage("§aTwój ekwipunek z backupu został przywrócony.");
        }

        // The async spawn hook already submitted this exact request to Paper. Movement or mouse
        // input between that packet and profile completion is not evidence of a missed arrival.
        if (prepared != null && prepared.matches(state.finalTargetData()) && validArrival(prepared.location())) {
            finishJoinAt(player, state, restored, null, true, state.forcedSector() != null);
            return;
        }
        if (sectorManager.isLockedSpawnBackend()) {
            Location requested = state.finalTargetData() == null ? null : locationFromJson(state.finalTargetData());
            Location spawn = validArrival(requested) ? requested : sectorManager.lockedSpawnLocation();
            if (requested == null && sectorManager.insideLockedSpawn(player.getLocation())) spawn = null;
            finishJoinAt(player, state, restored, spawn, state.finalTargetData() != null, state.forcedSector() != null);
            return;
        }

        if (state.finalTargetData() != null) {
            var target = gson.fromJson(state.finalTargetData(), com.google.gson.JsonObject.class);
            if (isSurfaceTarget(target)) {
                target.addProperty("y", 0);
                Location seed = locationFromJson(target.toString());
                finishRandomArrival(player, state, restored, seed, true);
                return;
            }
            Location finalTargetLocation = locationFromJson(state.finalTargetData());
            if (!validArrival(finalTargetLocation)) throw new IllegalArgumentException("Transfer target is outside this sector");
            finishJoinAt(player, state, restored, finalTargetLocation, true, false);
            return;
        }

        if (state.forcedSector() != null) {
            Sector targetSector = sectorManager.getSectorByName(state.forcedSector());
            Location sectorSpawn = targetSector == null ? null : sectorManager.getSectorSpawnLocation(targetSector);
            if (sectorSpawn == null) throw new IllegalArgumentException("Forced sector spawn is unavailable");
            finishJoinAt(player, state, restored, sectorSpawn, false, true);
            return;
        }

        if (state.playerData() == null) {
            Location recovery = sectorManager.recoveryLandingSeed(player.getLocation());
            if (recovery != null) {
                finishRandomArrival(player, state, restored, recovery, false);
                return;
            }
        }
        finishJoinAt(player, state, restored, state.playerData() == null ? null : locationFromJson(state.playerData()),
                false, false);
    }

    private void finishRandomArrival(Player player, JoinNetworkState state, RestoredTransfer restored, Location seed,
                                     boolean consumedFinal) {
        Sector sector = sectorManager.randomArrivalSector(seed);
        if (sector == null) throw new IllegalArgumentException("Random arrival is not in the local game sector");
        new RandomSurfaceLanding(plugin).find(player, seed, sector, destination -> {
            if (destination != null) {
                finishJoinAt(player, state, restored, destination, consumedFinal, false);
                return;
            }
            plugin.getLogger().warning("No safe random landing found in " + sector.getName());
            player.sendMessage("§eNie znaleziono bezpiecznego miejsca. Wracasz na spawn.");
            if (restored != null) {
                player.getInventory().setHeldItemSlot(restored.heldSlot());
                restored.flight().apply(player);
            }
            try {
                sectorManager.returnAfterFailedRandomArrival(player, sector).whenComplete((ignored, transferError) -> {
                    if (transferError != null) scheduleMainThread(() -> {
                        if (isCurrentSession(player)) player.kickPlayer("§cNie udało się wrócić na spawn. Połącz się ponownie.");
                    });
                });
            } catch (RuntimeException transferError) {
                plugin.getLogger().warning("Could not prepare safe return for " + player.getUniqueId() + ": " + transferError.getMessage());
                player.kickPlayer("§cNie udało się przygotować powrotu na spawn. Połącz się ponownie.");
            }
        });
    }

    private void finishJoinAt(Player player, JoinNetworkState state, RestoredTransfer restored, Location destination,
                              boolean consumedFinal, boolean consumedForced) {
        if (!isCurrentSession(player)) return;
            Runnable complete = () -> {
                if (!isCurrentSession(player)) return;
                User user = userManager.getUser(player);
                if (user == null) throw new IllegalStateException("Profile cache disappeared during join");
                 applyPersistentData(player, user);
                 if (restored != null) restored.flight().apply(player);
                 if (state.playerData() != null) {
                    player.setInvulnerable(true);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isCurrentSession(player)) {
                            User current = userManager.getUser(player);
                            player.setInvulnerable(current != null && current.isGodMode());
                        }
                    }, 100L);
                }
                clearConsumedJoinKeys(player.getUniqueId(), state, consumedFinal, consumedForced);
                plugin.getGuildTagManager().updateTagsFor(player);
                 if (restored != null) new HotbarSlotSync(plugin).ensureSelectedSlot(player, restored.heldSlot());
                sendWelcomePackage(player, state.respawning());
                if (consumedForced) player.sendMessage("§aZostałeś przeniesiony na sektor §e" + state.forcedSector() + "§a.");
            };
            if (destination == null) { complete.run(); return; }
            ai.aisector.utils.AsyncTeleportHandoff.start(() -> player.teleportAsync(destination),
                    task -> BukkitTaskScheduler.execute(plugin, task), () -> isCurrentSession(player), complete,
                    error -> {
                        plugin.getLogger().severe("Join teleport failed; Redis state retained for " + player.getUniqueId());
                        player.kickPlayer("§cNie udało się bezpiecznie przywrócić lokalizacji. Połącz się ponownie.");
                    });
    }

    private void clearConsumedJoinKeys(UUID playerUuid, JoinNetworkState state,
                                       boolean consumedFinalTarget, boolean consumedForcedSector) {
        redisManager.executeAsync(jedis -> {
            if (state.respawning()) {
                deleteIfUnchanged(jedis, "player:is_respawning:" + playerUuid, state.respawnMarker());
            }
            if (state.playerData() != null) {
                deleteIfUnchanged(jedis, "player:data:" + playerUuid, state.playerData());
            }
            if (state.pendingBackup() != null) {
                deleteIfUnchanged(jedis, "player:pending_backup:" + playerUuid, state.pendingBackup());
            }
            if (consumedFinalTarget && state.finalTargetData() != null) {
                deleteIfUnchanged(jedis, "player:final_teleport_target:" + playerUuid,
                        state.finalTargetData());
            }
            if (consumedForcedSector && state.forcedSector() != null) {
                deleteIfUnchanged(jedis, "player:force_sector_spawn:" + playerUuid, state.forcedSector());
            }
            return null;
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not clear consumed join state for " + playerUuid + ": "
                    + error.getMessage());
            return null;
        });
    }

    private void deleteIfUnchanged(redis.clients.jedis.Jedis jedis, String key, String expectedValue) {
        jedis.eval(DELETE_IF_UNCHANGED_SCRIPT, Collections.singletonList(key),
                Collections.singletonList(expectedValue));
    }

    private void applyPersistentData(Player player, User user) {
        player.setInvulnerable(user.isGodMode());
        player.setWalkSpeed(user.getWalkSpeed());
        player.setFlySpeed(user.getFlySpeed());

        if (user.isVanished()) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                    onlinePlayer.hidePlayer(plugin, player);
                }
            }
        }
    }


    private RestoredTransfer loadTransferData(Player player, String playerData) {
        if (playerData == null || playerData.length() > 4_194_304) {
            throw new IllegalArgumentException("Transfer data is empty or exceeds the 4 MiB limit");
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(playerData, type);
        if (data == null) {
            throw new IllegalArgumentException("Transfer data is empty");
        }
        Object dataVersionValue = data.getOrDefault("dataVersion", 1);
        if (!(dataVersionValue instanceof Number dataVersionNumber)) {
            throw new IllegalArgumentException("Unsupported transfer data version: " + dataVersionValue);
        }
        int dataVersion = dataVersionNumber.intValue();
        if (dataVersion != 1 && dataVersion != 2) {
            throw new IllegalArgumentException("Unsupported transfer data version: " + dataVersionValue);
        }
        if (!(data.get("health") instanceof Number healthValue)
                || !(data.get("hunger") instanceof Number hungerValue)
                || !(data.get("gameMode") instanceof String gameModeName)) {
            throw new IllegalArgumentException("Transfer data is missing required player state");
        }
        if (!(data.get("inventory") instanceof String inventoryData)
                || !(data.get("armor") instanceof String armorData)) {
            throw new IllegalArgumentException("Transfer data is missing inventory or armor");
        }
        validateSnapshotLocation(data);
        double health = healthValue.doubleValue();
        if (!Double.isFinite(health) || health < 0.0 || health > 1_000_000.0) {
            throw new IllegalArgumentException("Invalid transfer health value");
        }
        int hunger = TransferPayloadValidation.boundedInteger(hungerValue, "hunger", 0, 20);
        GameMode gameMode = GameMode.valueOf(gameModeName);
        Boolean activeFlight = data.get("flying") instanceof Boolean value ? value : null;
        int heldSlot = data.containsKey("heldSlot")
                ? TransferPayloadValidation.boundedInteger(data.get("heldSlot"), "heldSlot", 0, 8) : 0;
        int level = TransferPayloadValidation.boundedInteger(data.getOrDefault("level", 0), "level", 0, 1_000_000);
        int experience = TransferPayloadValidation.boundedInteger(data.getOrDefault("experience", 0),
                "experience", 0, Integer.MAX_VALUE);
        float exp = TransferPayloadValidation.boundedFloat(data.getOrDefault("exp", 0.0), "exp", 0.0f, 0.999999f);
        Float saturation = data.get("saturation") instanceof Number value
                ? TransferPayloadValidation.boundedFloat(value, "saturation", 0.0f, 20.0f) : null;

        ItemStack[] inventoryContents = null;
        ItemStack[] armorContents = null;
        ItemStack offhandItem = null;
        try {
            inventoryContents = itemStackArrayFromBase64(inventoryData, dataVersion);
            armorContents = itemStackArrayFromBase64(armorData, dataVersion);
            offhandItem = itemStackFromBase64((String) data.get("offhand"), dataVersion);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not decode transfer inventory for " + player.getName(), e);
        }
        Collection<PotionEffect> effects = deserializePotionEffects(data.get("effects"));

        // Location was validated above; finishJoinAt performs one async teleport to the final destination.

        player.setGameMode(gameMode);
        User user = userManager.getUser(player);
        if (user == null) throw new IllegalStateException("Missing profile during flight restore");
        PlayerFlight flight = PlayerFlight.resolve(gameMode, user.isFlying(), activeFlight);
        flight.apply(player);
        player.setHealth(Math.max(0.1, Math.min(health, player.getMaxHealth())));
        player.setFoodLevel(hunger);
        if (saturation != null) {
            player.setSaturation(Math.min(player.getFoodLevel(), saturation));
        }
        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(experience);
        player.getInventory().setContents(inventoryContents);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setItemInOffHand(offhandItem);
        for (PotionEffect currentEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(currentEffect.getType());
        }
        if (effects != null && !effects.isEmpty()) {
            player.addPotionEffects(effects);
        }

        return new RestoredTransfer(heldSlot, flight);
    }



    private void sendWelcomePackage(Player player, boolean respawning) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentSession(player)) return;
            Location arrival = player.getLocation();
            Sector sector = sectorManager.getSectorAt(arrival);
            borderManager.refreshAfterJoin(player, sector, arrival);
            if (respawning) showDeathTitle(player);
            else if (sector != null) {
                MessageUtil.sendTitle(player, "", "§7Połączono z sektorem §9" + sector.getName(), 300, 1000, 300);
            }
        }, 2L);
    }
    private void showDeathTitle(Player player) {
        // Używamy Title.Times, aby tytuł był widoczny dłużej
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500),  // fadeIn
                java.time.Duration.ofMillis(1500), // stay
                java.time.Duration.ofMillis(500)  // fadeOut
        );
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                Component.text("UMARŁEŚ!", NamedTextColor.RED), // Czerwony, główny tytuł
                Component.text(""), // Pusty podtytuł
                times
        );
        player.showTitle(title);

    }

    private Location locationFromJson(String json) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(json, type);
        validateSnapshotLocation(data);
        String worldName = (String) data.get("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("Serialized location references unloaded world " + worldName);
        return new Location(world, ((Number) data.get("x")).doubleValue(), ((Number) data.get("y")).doubleValue(),
                ((Number) data.get("z")).doubleValue(), data.get("yaw") instanceof Number yaw ? yaw.floatValue() : 0,
                data.get("pitch") instanceof Number pitch ? pitch.floatValue() : 0);
    }

    private void validateSnapshotLocation(Map<String, Object> data) {
        if (data == null || !(data.get("world") instanceof String worldName)
                || !(data.get("x") instanceof Number x)
                || !(data.get("y") instanceof Number y)
                || !(data.get("z") instanceof Number z)) {
            throw new IllegalArgumentException("Invalid serialized location");
        }
        if (worldName.isBlank() || worldName.length() > 128) {
            throw new IllegalArgumentException("Invalid serialized world name");
        }
        double xValue = x.doubleValue();
        double yValue = y.doubleValue();
        double zValue = z.doubleValue();
        float yaw = data.get("yaw") instanceof Number value ? value.floatValue() : 0.0f;
        float pitch = data.get("pitch") instanceof Number value ? value.floatValue() : 0.0f;
        TransferPayloadValidation.validateLocation(xValue, yValue, zValue, yaw, pitch);
    }

    private Set<PotionEffect> deserializePotionEffects(Object serializedValue) {
        Set<PotionEffect> effects = new HashSet<>();
        if (!(serializedValue instanceof List<?> serializedEffects)) return effects;
        if (serializedEffects.size() > 256) {
            throw new IllegalArgumentException("Too many potion effects in transfer data");
        }
        for (Object serializedEffect : serializedEffects) {
            if (!(serializedEffect instanceof Map<?, ?> effectData)
                    || !(effectData.get("type") instanceof String typeName)
                    || !(effectData.get("duration") instanceof Number duration)
                    || !(effectData.get("amplifier") instanceof Number amplifier)) continue;
            PotionEffectType effectType = PotionEffectType.getByName(typeName);
            if (effectType == null) continue;
            try {
                effects.add(new PotionEffect(effectType,
                        TransferPayloadValidation.boundedInteger(duration, "potion.duration", 0, 1_000_000),
                        TransferPayloadValidation.boundedInteger(amplifier, "potion.amplifier", 0, 255)));
            } catch (IllegalArgumentException malformedEffect) {
                // Ignore an invalid effect without discarding the player's complete transfer snapshot.
            }
        }
        return effects;
    }

    private ItemStack itemStackFromBase64(String data, int dataVersion) throws IOException {
        if (data == null) return new ItemStack(Material.AIR);
        if (dataVersion == 2) {
            try {
                return ItemStack.deserializeBytes(decodeVersionTwoPayload(data));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid version 2 item data", exception);
            }
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data, int dataVersion) throws IOException {
        if (data == null) return new ItemStack[0];
        if (dataVersion == 2) {
            try {
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(decodeVersionTwoPayload(data));
                if (items.length > 512) {
                    throw new IOException("Invalid item array length: " + items.length);
                }
                return items;
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid version 2 item array", exception);
            }
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            if (length < 0 || length > 512) {
                throw new IOException("Invalid item array length: " + length);
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    private byte[] decodeVersionTwoPayload(String data) throws IOException {
        if (!data.startsWith("v2:")) {
            throw new IOException("Version 2 item data has no v2 prefix");
        }
        try {
            return Base64.getDecoder().decode(data.substring(3));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Base64 item data", exception);
        }
    }
}
