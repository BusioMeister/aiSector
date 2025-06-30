package ai.aisector.sectors;

import ai.aisector.database.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
            Bukkit.getLogger().info("[AISector] Plik sectors.yml nie istnieje. Tworzenie domyślnego...");
            file.getParentFile().mkdirs();

            // Tworzenie domyślnej konfiguracji z dwoma sektorami
            YamlConfiguration config = new YamlConfiguration();

            ConfigurationSection section1 = config.createSection("sectors.Sector1");
            section1.set("minX", 0);
            section1.set("maxX", 99);
            section1.set("minZ", 0);
            section1.set("maxZ", 99);

            ConfigurationSection section2 = config.createSection("sectors.Sector2");
            section2.set("minX", 100);
            section2.set("maxX", 199);
            section2.set("minZ", 0);
            section2.set("maxZ", 99);

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

        Bukkit.getLogger().info("[AISector] Załadowano sektory: " + SECTORS.size());
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
    public void applyBorder(Player player, Sector sector) {
        WorldBorder border = player.getWorld().getWorldBorder();

        double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0; // +0.5 by centrować na blok
        double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;
        double sizeX = sector.getMaxX() - sector.getMinX() + 1;
        double sizeZ = sector.getMaxZ() - sector.getMinZ() + 1;
        double size = Math.max(sizeX, sizeZ)+ 3 ;

        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setWarningDistance(0); // możesz dostosować
        border.setWarningTime(0);
    }
    public Sector getSectorByName(String name) {
        return SECTORS.stream()
                .filter(sector -> sector.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<Sector> getSECTORS() {
        return SECTORS;
    }
}
