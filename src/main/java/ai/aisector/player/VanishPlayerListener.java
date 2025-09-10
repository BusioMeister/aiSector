package ai.aisector.player;

import ai.aisector.database.RedisManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import redis.clients.jedis.Jedis;

public class VanishPlayerListener implements Listener {

    private final VanishManager vanishManager;
    private final RedisManager redisManager;

    public VanishPlayerListener(VanishManager vanishManager, RedisManager redisManager) {
        this.vanishManager = vanishManager;
        this.redisManager = redisManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Ukryj przed nowym graczem adminów w vanishu
        vanishManager.handlePlayerJoin(player);

        // Sprawdź, czy sam dołączający gracz powinien być w vanishu
        try (Jedis jedis = redisManager.getJedis()) {
            if (jedis.sismember("aisector:vanished_players", player.getUniqueId().toString())) {
                vanishManager.vanish(player);
            }
        }
    }
}