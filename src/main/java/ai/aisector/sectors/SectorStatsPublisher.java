package ai.aisector.sectors;

import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class SectorStatsPublisher extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final String sectorName;
    private final AtomicLong lastWarningNanos = new AtomicLong();

    public SectorStatsPublisher(JavaPlugin plugin, RedisManager redisManager, String sectorName) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorName = sectorName;
    }

    @Override
    public void run() {
        double tps = Bukkit.getServer().getTPS()[0];
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;

        JsonObject stats = new JsonObject();
        stats.addProperty("sectorName", sectorName);
        stats.addProperty("tps", String.format(Locale.ROOT, "%.2f", tps));
        stats.addProperty("ram", usedMemory);
        String payload = stats.toString();

        redisManager.publishAsync(RedisChannels.SECTOR_STATS, payload)
                .exceptionally(error -> {
            long now = System.nanoTime();
            long previous = lastWarningNanos.get();
            if (now - previous >= 30_000_000_000L && lastWarningNanos.compareAndSet(previous, now)) {
                plugin.getLogger().warning("Nie udało się opublikować statystyk sektora: " + error.getMessage());
            }
            return null;
        });
    }
}
