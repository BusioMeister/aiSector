package ai.aisector;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public class GlobalChatPlugin implements Listener, PluginMessageListener {

    private final JavaPlugin plugin;
    private static final String CHANNEL = "global:chat";

    public GlobalChatPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        event.setCancelled(true);
        String formattedMessage = "[" + player.getWorld().getName() + "] " + player.getName() + ": " + message;
        plugin.getLogger().info("Wysyłam plugin message: " + formattedMessage);
        sendGlobalChatMessage(formattedMessage);
    }

    private void sendGlobalChatMessage(String message) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);
            out.writeUTF(message);

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPluginMessage(plugin, CHANNEL, byteStream.toByteArray());
                break; // wysyłamy tylko do jednego gracza, bo to plugin message proxy
            }

            out.close();
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd przy wysyłaniu wiadomości globalnej: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        plugin.getLogger().info("Odebrano plugin message na kanale " + channel + " od " + player.getName());

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String msg = in.readUTF();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(msg);
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd przy odbieraniu wiadomości globalnej: " + e.getMessage());
        }
    }
}
