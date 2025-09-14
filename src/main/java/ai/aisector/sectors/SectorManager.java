package ai.aisector.sectors;

import ai.aisector.database.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.util.*;

public class SectorManager {

    private final RedisManager redisManager;
    private final Map<String, Sector> sectors = new HashMap<>();

    public SectorManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        loadSectorsFromFile();
    }
    public void loadSectorsFromFile() {
        File file = new File("plugins/AISector/sectors.yml");
        if (!file.exists()) {
            Bukkit.getLogger().info("[AISector] Plik sectors.yml nie istnieje. Tworzenie domyślnego...");
            file.getParentFile().mkdirs();

            // Tworzenie domyślnej konfiguracji z dwoma sektorami
            YamlConfiguration config = new YamlConfiguration();

            ConfigurationSection section1 = config.createSection("sectors.Sector1");
            section1.set("minX", -200);
            section1.set("maxX", 199);
            section1.set("minZ", -200);
            section1.set("maxZ", 199);

            ConfigurationSection section2 = config.createSection("sectors.Sector2");
            section2.set("minX", -500);
            section2.set("maxX", -201);
            section2.set("minZ", -300);
            section2.set("maxZ", 299);

            ConfigurationSection section3 = config.createSection("sectors.Sector3");
            section3.set("minX", 200);
            section3.set("maxX", 499);
            section3.set("minZ", -300);
            section3.set("maxZ", 299);

            try {
                config.save(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sectorSection = config.getConfigurationSection("sectors");
        if (sectorSection == null) return;

        sectors.clear();
        for (String key : sectorSection.getKeys(false)) {
            ConfigurationSection s = sectorSection.getConfigurationSection(key);
            if (s == null) continue;

            int minX = s.getInt("minX");
            int maxX = s.getInt("maxX");
            int minZ = s.getInt("minZ");
            int maxZ = s.getInt("maxZ");

            // Dodajemy sektor do Mapy, używając małych liter jako klucza dla spójności
            sectors.put(key.toLowerCase(), new Sector(key, minX, maxX, minZ, maxZ));
        }
        Bukkit.getLogger().info("[AISector] Załadowano sektory: " + sectors.size());
    }


    // Używamy nowej, bezpieczniejszej konstrukcji try-with-resources
    public void transferPlayer(UUID uuid, String sectorId) {
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("sector-transfer", uuid + ":" + sectorId);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        return sectors.get(name.toLowerCase());
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
        return sectors.values();
    }

    public Location getSectorSpawnLocation(Sector sector) {
        if (sector == null) return null;
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;
        double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0;
        double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;
        double y = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1.5;
        return new Location(world, centerX, y, centerZ, 0, 0);
    }
}