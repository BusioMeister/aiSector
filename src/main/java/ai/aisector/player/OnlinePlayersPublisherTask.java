package ai.aisector.player;

import ai.aisector.database.RedisManager;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OnlinePlayersPublisherTask extends BukkitRunnable {

    private final RedisManager redisManager;
    private final String sectorName;

    public OnlinePlayersPublisherTask(RedisManager redisManager, String sectorName) {
        this.redisManager = redisManager;
        this.sectorName = sectorName;
    }

    @Override
    public void run() {
        Map<String, String> playersMap = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playersMap.put(player.getUniqueId().toString(), player.getName());
        }

        // Możesz użyć prostego JSONa (np. Gson albo ręcznie) - tutaj prosty JSON ręczny:
        String json = new Gson().toJson(playersMap);


        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("sector-online:" + sectorName, json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
