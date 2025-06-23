package ai.aisector;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

public class PlayerQuitListener implements Listener {

    private final RedisManager redisManager;
    private final JavaPlugin plugin;

    public PlayerQuitListener(JavaPlugin plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

    }
}
