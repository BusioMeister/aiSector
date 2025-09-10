package ai.aisector.listeners; // Upewnij się, że pakiet jest poprawny

import ai.aisector.database.RedisManager;
import ai.aisector.player.PlayerDataSerializer; // Potrzebujemy dostępu do serializera
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class PlayerDataSaveListener extends JedisPubSub implements Runnable {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public PlayerDataSaveListener(JavaPlugin plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
    }

    @Override
    public void run() {
        try (Jedis jedis = redisManager.getJedis()) {
            // Ta subskrypcja blokuje wątek, dlatego uruchamiamy ją w osobnym wątku.
            jedis.subscribe(this, "aisector:save_player_data");
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd połączenia z Redis w PlayerDataSaveListener: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!channel.equals("aisector:save_player_data")) {
            return;
        }

        JsonObject data = gson.fromJson(message, JsonObject.class);
        UUID playerUuid = UUID.fromString(data.get("uuid").getAsString());

        // Musimy wykonać operacje na graczu w głównym wątku serwera.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // Serializujemy i zapisujemy dane gracza
                String playerData = PlayerDataSerializer.serialize(player, player.getLocation());
                try (Jedis jedis = redisManager.getJedis()) {
                    // Klucz "player:data:" to ten sam klucz, którego nasłuchuje PlayerJoinListener
                    jedis.setex("player:data:" + player.getUniqueId().toString(), 60, playerData);
                    plugin.getLogger().info("Zapisano dane (ekwipunek itp.) dla gracza " + player.getName() + " przed transferem.");
                }
            }
        });
    }
}