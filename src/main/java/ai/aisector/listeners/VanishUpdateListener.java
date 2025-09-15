package ai.aisector.listeners;

import ai.aisector.sectors.player.VanishManager;
import ai.aisector.sectors.player.VanishTagManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

public class VanishUpdateListener extends JedisPubSub {

    private final JavaPlugin plugin;
    private final VanishTagManager tagManager; // <-- NOWE POLE
    private final Gson gson = new Gson();

    public VanishUpdateListener(JavaPlugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.tagManager = new VanishTagManager();

    }

    @Override
    public void onMessage(String channel, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonObject data = gson.fromJson(message, JsonObject.class);

            if (channel.equals("aisector:vanish_update")) {
                String name = data.get("name").getAsString();
                String action = data.get("action").getAsString();

                // Jeśli gracz nie jest na tym serwerze, i tak zaktualizuj jego tag dla lokalnych adminów
                if (Bukkit.getPlayer(name) == null) {
                    if (action.equals("VANISH")) {
                        tagManager.setVanishedTag(name);
                    } else {
                        tagManager.removeVanishedTag(name);
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