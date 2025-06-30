package ai.aisector.player;

import ai.aisector.database.RedisManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
