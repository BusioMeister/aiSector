package ai.aisector.sectors;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;

import ai.aisector.utils.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SectorManager {
    private static final int MAX_TRANSFER_KEYS = 4;
    private static final int MAX_TRANSFER_VALUE_CHARS = 2_097_152;
    private static final String PUBLISH_TRANSFER_SCRIPT = "local count = tonumber(ARGV[1]); "
            + "local ttl = tonumber(ARGV[2]); "
            + "for i = 1, count do redis.call('setex', KEYS[i], ttl, ARGV[i + 2]); end; "
            + "local subscribers = redis.call('publish', ARGV[count + 3], ARGV[count + 4]); "
            + "if subscribers == 0 then "
            + "for i = 1, count do "
            + "if redis.call('get', KEYS[i]) == ARGV[i + 2] then redis.call('del', KEYS[i]); end; "
            + "end; end; return subscribers";

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final Map<String, Sector> sectors = new HashMap<>();
    private SpawnLayout spawnLayout;
    private SeamChunkWarmer seamChunkWarmer;

    public SectorManager(SectorPlugin plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        loadSectorsFromFile();
    }
    public void loadSectorsFromFile() {
        File file = new File(plugin.getDataFolder(), "sectors.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("sectors.yml", false);
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create sectors.yml: " + e.getMessage());
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sectorSection = config.getConfigurationSection("sectors");
        if (sectorSection == null) {
            plugin.getLogger().warning("No 'sectors' section found in " + file.getPath());
            return;
        }

        Map<String, Sector> candidate = new HashMap<>();
        boolean malformed = false;
        for (String key : sectorSection.getKeys(false)) {
            ConfigurationSection s = sectorSection.getConfigurationSection(key);
            if (s == null) {
                plugin.getLogger().severe("Invalid sector definition '" + key + "': expected a section.");
                malformed = true;
                continue;
            }
            Integer minX = strictCoordinate(s, "minX");
            Integer maxX = strictCoordinate(s, "maxX");
            Integer minZ = strictCoordinate(s, "minZ");
            Integer maxZ = strictCoordinate(s, "maxZ");
            if (minX == null || maxX == null || minZ == null || maxZ == null) {
                plugin.getLogger().severe("Invalid integer bounds in sector definition '" + key + "'.");
                malformed = true;
                continue;
            }
            String normalizedName = normalizeSectorName(key);
            if (candidate.putIfAbsent(normalizedName, new Sector(key, minX, maxX, minZ, maxZ)) != null) {
                plugin.getLogger().severe("Duplicate sector name ignoring case: '" + key + "'.");
                malformed = true;
            }
        }
        String validationError = SectorDefinitionValidation.validate(candidate.values());
        if (malformed || validationError != null || candidate.isEmpty()) {
            plugin.getLogger().severe("Sector configuration rejected without partial registration: "
                    + (validationError == null ? "malformed or empty definition" : validationError));
            return;
        }
        SpawnLayout candidateLayout = null;
        if (config.contains("locked-spawn")) {
            try {
                ConfigurationSection spawn = config.getConfigurationSection("locked-spawn");
                if (spawn == null) throw new IllegalArgumentException("Expected locked-spawn section");
                candidateLayout = SpawnLayout.create(candidate, spawn.getString("sector"),
                        spawn.getString("spawn-world"), spawn.getString("game-world"), spawn.getStringList("game-sectors"));
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().severe("Locked spawn configuration rejected: " + invalid.getMessage());
                return;
            }
        }
        sectors.clear();
        sectors.putAll(candidate);
        spawnLayout = candidateLayout;
        plugin.getLogger().info("Załadowano sektory: " + sectors.size());
    }

    private Integer strictCoordinate(ConfigurationSection section, String key) {
        Object raw = section.get(key);
        if (!(raw instanceof Number number)) return null;
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < -30_000_000 || value > 30_000_000) return null;
        return (int) value;
    }


    // Wyszukiwanie jest teraz bardziej wydajne dzięki pętli po wartościach mapy
    public String getSectorForLocation(int x, int z) {
        for (Sector s : sectors.values()) {
            if (s.isInside(x, z)) return s.getName();
        }
        return "";
    }

    public Sector getSector(int x, int z) {
        for (Sector s : sectors.values()) {
            if (s.isInside(x, z)) return s;
        }
        return null;
    }

    // Ta metoda jest teraz natychmiastowa dzięki użyciu Mapy
    public Sector getSectorByName(String name) {
        String key = normalizeSectorName(name);
        return key == null ? null : sectors.get(key);
    }

    public boolean isLockedSpawnBackend() {
        return spawnLayout != null && isLocalSector(spawnLayout.spawn());
    }

    public void warmGameSeam() {
        if (spawnLayout == null || spawnLayout.game().stream().noneMatch(this::isLocalSector)) return;
        World world = Bukkit.getWorld(spawnLayout.gameWorld());
        if (world == null) {
            plugin.getLogger().warning("Cannot warm sector seam: game world is not loaded");
            return;
        }
        try {
            List<SeamChunkPlan> chunks = SeamChunkPlan.between(spawnLayout.game().getFirst(), spawnLayout.game().getLast());
            stopWarmingGameSeam();
            seamChunkWarmer = new SeamChunkWarmer(plugin, world, chunks);
            seamChunkWarmer.start();
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Cannot warm sector seam: " + invalid.getMessage());
        }
    }

    public void stopWarmingGameSeam() {
        if (seamChunkWarmer != null) {
            seamChunkWarmer.close();
            seamChunkWarmer = null;
        }
    }

    public boolean owns(Location location) {
        Sector sector = getSectorAt(location);
        return sector != null && isLocalSector(sector);
    }

    public Sector getSectorAt(Location location) {
        if (!isSectorWorld(location)) return null;
        return getSector(location.getBlockX(), location.getBlockZ());
    }

    public Sector localSectorAtWorld(Location location) {
        if (!isSectorWorld(location)) return null;
        return getSectorByName(plugin.getConfig().getString("this-sector-name", ""));
    }

    private boolean isSectorWorld(Location location) {
        if (spawnLayout != null) {
            String world = isLockedSpawnBackend() ? spawnLayout.spawnWorld() : spawnLayout.gameWorld();
            return world.equals(location.getWorld().getName());
        }
        return true;
    }

    boolean isLocalSector(Sector sector) {
        return sector.getName().equalsIgnoreCase(plugin.getConfig().getString("this-sector-name", ""));
    }

    public boolean insideLockedSpawn(Location location) {
        return isLockedSpawnBackend() && location.getWorld().getName().equals(spawnLayout.spawnWorld())
                && spawnLayout.spawn().isInside(location.getBlockX(), location.getBlockZ());
    }

    public Location lockedSpawnLocation() {
        World world = Bukkit.getWorld(spawnLayout.spawnWorld());
        if (world == null) throw new IllegalStateException("Locked spawn world is unavailable");
        Location spawn = world.getSpawnLocation();
        if (!insideLockedSpawn(spawn)) throw new IllegalStateException("World spawn must be inside the locked spawn sector");
        return spawn;
    }

    private record RandomExit(String sector, String targetJson) { }

    private RandomExit chooseSpawnExit() {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        Sector sector = spawnLayout.choose(random);
        return new RandomExit(sector.getName(), new org.bson.Document("world", spawnLayout.gameWorld())
                .append("x", SpawnLayout.coordinate(random, sector.getMinX(), sector.getMaxX()) + 0.5)
                .append("z", SpawnLayout.coordinate(random, sector.getMinZ(), sector.getMaxZ()) + 0.5)
                .append("surface", true).toJson());
    }

    public Sector randomArrivalSector(Location location) {
        if (spawnLayout == null || !location.getWorld().getName().equals(spawnLayout.gameWorld())) return null;
        Sector local = getSector(location.getBlockX(), location.getBlockZ());
        return local != null && spawnLayout.game().contains(local) && isLocalSector(local) ? local : null;
    }

    public Location recoveryLandingSeed(Location location) {
        if (spawnLayout == null || isLockedSpawnBackend() || randomArrivalSector(location) != null) return null;
        World world = Bukkit.getWorld(spawnLayout.gameWorld());
        if (world == null) throw new IllegalStateException("Game world is unavailable");
        Sector local = spawnLayout.game().stream().filter(this::isLocalSector).findFirst().orElseThrow();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        return new Location(world, SpawnLayout.coordinate(random, local.getMinX(), local.getMaxX()) + .5,
                0, SpawnLayout.coordinate(random, local.getMinZ(), local.getMaxZ()) + .5);
    }

    CompletableFuture<Void> transferFromSpawnButton(Player player) {
        if (!insideLockedSpawn(player.getLocation()))
            return CompletableFuture.failedFuture(new IllegalStateException("Player is not on locked spawn"));
        var exit = chooseSpawnExit();
        var snapshot = plugin.getUserManager().serializePlayerDataForTransfer(player, player.getLocation());
        return transferPlayerAsync(player, exit.sector(), snapshot,
                 Map.of("player:final_teleport_target:" + player.getUniqueId(), exit.targetJson()), 60, "spawn-button", null);
    }

    public CompletableFuture<Void> returnAfterFailedRandomArrival(Player player, Sector failedSector) {
        if (spawnLayout == null || !spawnLayout.game().contains(failedSector) || !isLocalSector(failedSector))
            return CompletableFuture.failedFuture(new IllegalStateException("Not a locked-spawn game backend"));
        Location position = player.getLocation();
        String snapshot = plugin.getUserManager().serializePlayerDataForTransfer(player, position);
        // Overwrite the old random target atomically with the back-transfer. Spawn resolves
        // its own configured world spawn, so the source need not load that world's terrain.
        Sector spawn = spawnLayout.spawn();
        String target = new org.bson.Document("world", spawnLayout.spawnWorld())
                .append("x", spawn.getMinX() + .5).append("y", 0).append("z", spawn.getMinZ() + .5).toJson();
        return transferPlayerAsync(player, spawn.getName(), snapshot,
                Map.of("player:final_teleport_target:" + player.getUniqueId(), target));
    }

    /** Stores a captured player snapshot off-thread, then requests the proxy transfer. */
    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData) {
        return transferPlayerAsync(player, sectorId, playerData, Collections.emptyMap());
    }

    public CompletableFuture<Void> transferAcrossSeam(Player player, Sector destination, Location arrival,
                                                      String playerData, org.bukkit.event.player.PlayerMoveEvent move) {
        String target = new org.bson.Document("world", arrival.getWorld().getName())
                .append("x", arrival.getX()).append("y", arrival.getY()).append("z", arrival.getZ())
                .append("yaw", arrival.getYaw()).append("pitch", arrival.getPitch())
                .append("seam", destination.getName()).toJson();
        return transferPlayerAsync(player, destination.getName(), playerData,
                 Map.of("player:final_teleport_target:" + player.getUniqueId(), target), 60, null, move);
    }

    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData,
                                                       Map<String, String> transferKeys) {
        return transferPlayerAsync(player, sectorId, playerData, transferKeys, 60);
    }

    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData,
                                                       Map<String, String> transferKeys, int ttlSeconds) {
        return transferPlayerAsync(player, sectorId, playerData, transferKeys, ttlSeconds, null, null);
    }

    /** Called only after a home, guild-home or accepted TPA route completes its warmup. */
    public CompletableFuture<Void> transferAfterTeleportWarmup(Player player, String sectorId, String playerData,
                                                               String key, String target, int ttlSeconds) {
        String spawnExit = null;
        if (isLockedSpawnBackend()) {
            var location = org.bson.Document.parse(target);
            if (!key.equals("player:final_teleport_target:" + player.getUniqueId())
                    || !isValidSpawnWarmupTarget(sectorId, location)
                    || !insideLockedSpawn(player.getLocation())) {
                player.sendMessage(Messages.text("sector-manager.cel-teleportacji-nie-nalezy-do-sektora-gry"));
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Spawn warmup target"));
            }
            spawnExit = "spawn-warmup";
        }
        return transferPlayerAsync(player, sectorId, playerData, Map.of(key, target), ttlSeconds, spawnExit, null);
    }

    private boolean isValidSpawnWarmupTarget(String sectorId, org.bson.Document location) {
        if (sectorId == null || !spawnLayout.gameWorld().equals(location.get("world"))) return false;
        if (!(location.get("x") instanceof Number x) || !(location.get("y") instanceof Number y)
                || !(location.get("z") instanceof Number z) || !Double.isFinite(x.doubleValue())
                || !Double.isFinite(y.doubleValue()) || !Double.isFinite(z.doubleValue())) return false;
        return spawnLayout.game().stream().anyMatch(sector -> sector.getName().equalsIgnoreCase(sectorId)
                && sector.isInside((int) Math.floor(x.doubleValue()), (int) Math.floor(z.doubleValue())));
    }

    private CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData,
                                                         Map<String, String> transferKeys, int ttlSeconds, String spawnExit,
                                                        org.bukkit.event.player.PlayerMoveEvent initialSeamMove) {
        Objects.requireNonNull(player, "player");
        if (isLockedSpawnBackend() && spawnExit == null) {
            player.sendMessage(Messages.text("sector-manager.uzyj-przycisku-domu-bazy-gildii-albo-zaakceptowanego"));
            return CompletableFuture.failedFuture(new IllegalStateException("Missing authorized Spawn exit route"));
        }
        if (sectorId == null || !sectorId.matches("[A-Za-z0-9_-]{1,64}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid sector name"));
        }
        Objects.requireNonNull(transferKeys, "transferKeys");
        if (ttlSeconds <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Transfer TTL must be positive"));
        }

        UUID playerUuid = player.getUniqueId();
        if (transferKeys.size() > MAX_TRANSFER_KEYS
                || (playerData != null && playerData.length() > MAX_TRANSFER_VALUE_CHARS)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Transfer payload exceeds limits"));
        }
        for (Map.Entry<String, String> entry : transferKeys.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || value.length() > MAX_TRANSFER_VALUE_CHARS
                    || !isAllowedTransferKey(playerUuid, key)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid transfer metadata"));
            }
        }
        if ((long) (playerData == null ? 0 : playerData.length())
                + transferKeys.values().stream().mapToLong(String::length).sum()
                > MAX_TRANSFER_VALUE_CHARS) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Total transfer payload exceeds limit"));
        }
        String playerName = player.getName();
        Map<String, String> keysToWrite = new HashMap<>(transferKeys);
        CompletableFuture<Void> result = plugin.getUserManager().prepareTransferProfileAsync(player, initialSeamMove)
                .thenCompose(saved -> redisManager.<Void>executeAsync(jedis -> {
            List<String> keys = new ArrayList<>();
            List<String> arguments = new ArrayList<>();
            if (playerData != null) {
                keys.add("player:data:" + playerUuid);
                arguments.add(playerData);
            }
            keysToWrite.forEach((key, value) -> {
                keys.add(key);
                arguments.add(value);
            });
            arguments.add(0, Integer.toString(keys.size()));
            arguments.add(1, Integer.toString(ttlSeconds));
            arguments.add(RedisChannels.SECTOR_TRANSFER);
            arguments.add(playerUuid + ":" + sectorId + (spawnExit == null ? "" : ":" + spawnExit));

            long proxySubscribers = ((Number) jedis.eval(PUBLISH_TRANSFER_SCRIPT, keys, arguments)).longValue();
            if (proxySubscribers == 0L) {
                throw new IllegalStateException("No proxy is subscribed to sector transfers");
            }
            return null;
        }));

        result.whenComplete((ignored, error) -> {
            if (error == null) return;
            boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                plugin.getLogger().warning("Could not transfer " + playerName + " to " + sectorId
                        + ": " + error.getMessage());
                if (player.isOnline() && Bukkit.getPlayer(playerUuid) == player) {
                    player.sendMessage(Messages.text("sector-manager.transfer-nie-powiodl-sie-jesli-polaczenie-nie"));
                }
            });
            if (!scheduled) {
                plugin.getLogger().warning("Transfer failed during shutdown for " + playerUuid);
            }
        });
        return result;
    }

    static String normalizeSectorName(String name) {
        return name == null ? null : name.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isAllowedTransferKey(UUID playerUuid, String key) {
        return playerUuid != null && key != null
                && (key.equals("player:final_teleport_target:" + playerUuid)
                || key.equals("player:is_respawning:" + playerUuid));
    }

    // 🔥 TUTAJ JEST BRAKUJĄCA METODA 🔥
    /**
     * Oblicza odległość gracza do najbliższej krawędzi sektora, w którym się znajduje.
     * @param location Aktualna lokalizacja gracza.
     * @return Odległość w blokach do najbliższej granicy.
     */
    public double distanceToClosestBorder(Location location) {
        Sector sector = getSector(location.getBlockX(), location.getBlockZ());
        if (sector == null) {
            // Jeśli gracz jest poza sektorem, zwracamy dużą wartość, aby bossbar się nie pokazywał
            return Double.MAX_VALUE;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Obliczamy odległości do 4 krawędzi sektora
        double distToNorth = z - sector.getMinZ();
        double distToSouth = sector.getMaxZ() - z;
        double distToWest = x - sector.getMinX();
        double distToEast = sector.getMaxX() - x;

        // Zwracamy najmniejszą z tych odległości
        return Math.min(Math.min(distToNorth, distToSouth), Math.min(distToWest, distToEast));
    }

    public Collection<Sector> getSECTORS() {
        return List.copyOf(sectors.values());
    }

    public Location getSectorSpawnLocation(Sector sector) {
        if (sector == null) return null;
        World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (world == null) return null;
        double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0;
        double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;
        double y = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1.5;
        return new Location(world, centerX, y, centerZ, 0, 0);
    }
}
