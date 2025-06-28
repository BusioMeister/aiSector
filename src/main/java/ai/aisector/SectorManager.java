package ai.aisector;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.util.*;

public class SectorManager {

    private final RedisManager redisManager;
    private final List<Sector> SECTORS = new ArrayList<>();

    public SectorManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        loadSectorsFromFile(); // załaduj sektory z pliku
    }

    public void loadSectorsFromFile() {
        File file = new File("plugins/AISector/sectors.yml");
        if (!file.exists()) {
            System.out.println("[AISector] Plik sectors.yml nie istnieje. Tworzenie domyślnego...");
            file.getParentFile().mkdirs();

            // Domyślny sektor
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection section = config.createSection("sectors.Sector1");
            section.set("minX", 0);
            section.set("maxX", 99);
            section.set("minZ", 0);
            section.set("maxZ", 99);
            try {
                config.save(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sectorSection = config.getConfigurationSection("sectors");
        if (sectorSection == null) return;

        for (String key : sectorSection.getKeys(false)) {
            ConfigurationSection s = sectorSection.getConfigurationSection(key);
            if (s == null) continue;

            int minX = s.getInt("minX");
            int maxX = s.getInt("maxX");
            int minZ = s.getInt("minZ");
            int maxZ = s.getInt("maxZ");
            SECTORS.add(new Sector(key, minX, maxX, minZ, maxZ));
        }

        System.out.println("[AISector] Załadowano sektory: " + SECTORS.size());
    }

    public SectorData calculateSectorData(String sectorName) {
        for (Sector sector : SECTORS) {
            if (sector.getName().equalsIgnoreCase(sectorName)) {
                double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0;
                double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;
                double sizeX = (sector.getMaxX() - sector.getMinX() + 1);
                double sizeZ = (sector.getMaxZ() - sector.getMinZ() + 1);
                double size = Math.max(sizeX, sizeZ);
                return new SectorData(centerX, centerZ, size);
            }
        }
        return null; // zamiast rzucania wyjątku
    }

    public void transferPlayer(UUID uuid, String sectorId) {
        Jedis jedis = redisManager.getJedis();
        try {
            jedis.publish("sector-transfer", uuid + ":" + sectorId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            redisManager.releaseJedis(jedis);
        }
    }

    public String getSectorForLocation(int x, int z) {
        for (Sector s : SECTORS) {
            if (s.isInside(x, z)) return s.getName();
        }
        return "";
    }

    public Sector getSector(int x, int z) {
        for (Sector s : SECTORS) {
            if (s.isInside(x, z)) return s;
        }
        return null;
    }

    public List<Sector> getSECTORS() {
        return SECTORS;
    }
}
