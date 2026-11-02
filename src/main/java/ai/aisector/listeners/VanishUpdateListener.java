package ai.aisector.listeners;

import ai.aisector.sectors.player.VanishManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.player.VanishTagManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;
import ai.aisector.utils.BukkitTaskScheduler;

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
        if (message == null || message.length() > 2_048) {
            plugin.getLogger().warning("Ignoring empty or oversized vanish/admin message");
            return;
        }
        try {
            JsonObject data = gson.fromJson(message, JsonObject.class);
            if (data == null) return;

            if (channel.equals(RedisChannels.VANISH_UPDATE)) {
                VanishUpdatePayload payload = VanishUpdatePayload.parse(data).orElse(null);
                if (payload == null) return;
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (Bukkit.getPlayer(payload.playerUuid()) != null) return;
                    vanishManager.applyNetworkVanish(payload.playerUuid(), payload.playerName(),
                            payload.action() == VanishUpdatePayload.Action.VANISH);
                });
            } else if (channel.equals("aisector:admin_chat")) {
                if (!data.has("message") || !data.get("message").isJsonPrimitive()
                        || !data.getAsJsonPrimitive("message").isString()) return;
                String chatMessage = data.get("message").getAsString();
                if (chatMessage.length() > 1_024) return;
                BukkitTaskScheduler.execute(plugin, () -> Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("aisector.command.vanish"))
                        .forEach(p -> p.sendMessage(chatMessage)));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ignoring malformed vanish Redis message: " + exception.getMessage());
        }
    }
}
