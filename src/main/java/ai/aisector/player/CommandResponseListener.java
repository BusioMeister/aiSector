package ai.aisector.player;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

public class CommandResponseListener extends JedisPubSub {

    private final JavaPlugin plugin;
    private final Gson gson = new Gson();

    public CommandResponseListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessage(String channel, String message) {
        // Używamy schedulera, ponieważ operujemy w wątku Redis
        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonObject data = gson.fromJson(message, JsonObject.class);
            String playerName = data.get("playerName").getAsString();
            Player player = Bukkit.getPlayer(playerName);

            if (player == null) return;

            if (channel.equals("aisector:tp_execute_local")) {
                String targetName = data.get("targetName").getAsString();
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    player.teleport(target);
                    player.sendMessage("§aPomyślnie przeteleportowano do §e" + target.getName());
                }
            } else if (channel.equals("aisector:send_message")) {
                String chatMessage = data.get("message").getAsString();
                player.sendMessage(chatMessage);
            }
        });
    }
}