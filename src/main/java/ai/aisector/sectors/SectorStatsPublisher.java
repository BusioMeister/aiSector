package ai.aisector.sectors;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class SectorStatsPublisher extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final String sectorName;

    public SectorStatsPublisher(JavaPlugin plugin, RedisManager redisManager, String sectorName) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorName = sectorName;
    }

    @Override
    public void run() {
        try (Jedis jedis = redisManager.getJedis()) {
            JsonObject stats = new JsonObject();

            // Pobierz TPS (działa na Paper i nowszych wersjach Spigota)
            double tps = Bukkit.getServer().getTPS()[0]; // TPS z ostatniej minuty

            // Pobierz użycie RAM w MB
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;

            stats.addProperty("sectorName", this.sectorName);
            stats.addProperty("tps", String.format("%.2f", tps)); // Formatuj do 2 miejsc po przecinku
            stats.addProperty("ram", usedMemory);

            jedis.publish("aisector:sector_stats", stats.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udało się opublikować statystyk sektora: " + e.getMessage());
        }
    }
}