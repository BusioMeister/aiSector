package ai.aisector.sectors;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
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
        sectors.clear();
        sectors.putAll(candidate);
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

    /** Stores a captured player snapshot off-thread, then requests the proxy transfer. */
    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData) {
        return transferPlayerAsync(player, sectorId, playerData, Collections.emptyMap());
    }

    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData,
                                                       Map<String, String> transferKeys) {
        return transferPlayerAsync(player, sectorId, playerData, transferKeys, 60);
    }

    public CompletableFuture<Void> transferPlayerAsync(Player player, String sectorId, String playerData,
                                                       Map<String, String> transferKeys, int ttlSeconds) {
        Objects.requireNonNull(player, "player");
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
        CompletableFuture<Void> result = plugin.getUserManager().prepareTransferProfileAsync(player)
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
            arguments.add(playerUuid + ":" + sectorId);

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
                    player.sendMessage("§cTransfer nie powiódł się. Jeśli połączenie nie zostanie zakończone, połącz się ponownie.");
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
