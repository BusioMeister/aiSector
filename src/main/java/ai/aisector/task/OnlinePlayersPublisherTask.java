package ai.aisector.task;

import ai.aisector.shared.RedisChannels;
import ai.aisector.database.RedisManager;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class OnlinePlayersPublisherTask extends BukkitRunnable {
    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final String sectorName;
    private final AtomicLong lastWarningNanos = new AtomicLong();

    public OnlinePlayersPublisherTask(JavaPlugin plugin, RedisManager redisManager, String sectorName) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorName = sectorName;
    }

    @Override
    public void run() {
        Map<String, String> playersMap = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playersMap.put(player.getUniqueId().toString(), player.getName());
        }

        String json = GSON.toJson(playersMap);
        redisManager.publishAsync(RedisChannels.onlinePlayersForServer(sectorName), json)
                .exceptionally(error -> {
            long now = System.nanoTime();
            long previous = lastWarningNanos.get();
            if (now - previous >= 30_000_000_000L && lastWarningNanos.compareAndSet(previous, now)) {
                plugin.getLogger().warning("Could not publish online players for " + sectorName + "; retrying: "
                        + error.getMessage());
            }
            return null;
        });
    }
}
