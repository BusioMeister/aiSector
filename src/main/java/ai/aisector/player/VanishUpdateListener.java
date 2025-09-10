package ai.aisector.player;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class VanishUpdateListener extends JedisPubSub {

    private final JavaPlugin plugin;
    private final VanishManager vanishManager;
    private final Gson gson = new Gson();

    public VanishUpdateListener(JavaPlugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
    }

    @Override
    public void onMessage(String channel, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonObject data = gson.fromJson(message, JsonObject.class);

            if (channel.equals("aisector:vanish_update")) {
                UUID uuid = UUID.fromString(data.get("uuid").getAsString());
                String action = data.get("action").getAsString();
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) { // Aplikuj tylko jeśli gracz jest na tym serwerze
                    if (action.equals("VANISH")) {
                        vanishManager.vanish(player);
                    } else {
                        vanishManager.unvanish(player);
                    }
                }
            } else if (channel.equals("aisector:admin_chat")) {
                String chatMessage = data.get("message").getAsString();
                // Wyślij wiadomość do wszystkich adminów online
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("aisector.command.vanish"))
                        .forEach(p -> p.sendMessage(chatMessage));
            }
        });
    }
}