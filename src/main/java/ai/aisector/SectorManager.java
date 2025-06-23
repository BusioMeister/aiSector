package ai.aisector;

import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.UUID;


public class SectorManager {

    private RedisManager redisManager;
    private List<Sector> SECTORS =  List.of(
            new Sector("Sector1", 0, 100, 0, 100),  // Pierwszy sektor o wymiarach 100x100
            new Sector("Sector2", 100, 200, 0, 100) // Drugi sektor obok pierwszego, również 100x100
    );
    public SectorData calculateSectorData(String sectorName) {
        for (Sector sector : SECTORS) {
            if (sector.getName().equalsIgnoreCase(sectorName)) {
                // Obliczanie środka (centerX, centerZ)
                double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0;
                double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;

                // Obliczanie rozmiaru (największa odległość między granicami sektora)
                double size = Math.max(
                        sector.getMaxX() - sector.getMinX(),
                        sector.getMaxZ() - sector.getMinZ()
                );

                return new SectorData(centerX, centerZ, size);
            }
        }
        throw new IllegalArgumentException("Nie znaleziono sektora o nazwie: " + sectorName);
    }

    public SectorManager(RedisManager redisManager) {
        this.redisManager = redisManager;

    }

    // Przykładowa metoda do transferu gracza między sektorami
    public void transferPlayer(UUID uuid, String sectorId) {
        Jedis jedis = redisManager.getJedis();
        try {
            // Przykładowa operacja na Redis
            jedis.publish("sector-transfer", uuid + ":" + sectorId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            redisManager.releaseJedis(jedis);
        }
    }
    public String getSectorForLocation(int x, int z) {
        for (Sector SECTOR : SECTORS) {
            if (SECTOR.isInside(x, z)) {
                return SECTOR.getName();
            }
        }
        return "";
    }

    public List<Sector> getSECTORS() {
        return SECTORS;
    }
    public Sector getSector(int x, int z) {
        for (Sector SECTOR : SECTORS) {
            if (SECTOR.isInside(x, z)) {
                return SECTOR;
            }
        }
        return null;
    }

}