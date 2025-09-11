package ai.aisector.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteArrayDataInput;

public class GlobalChatPlugin implements Listener, PluginMessageListener {

    private final JavaPlugin plugin;
    private static final String CHANNEL = "global:chat";

    public GlobalChatPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        // 🔥 POPRAWKA 1: Rejestrujemy kanał przychodzący TYLKO dla jednego, stałego gracza (lub konsoli) 🔥
        // To zapobiega wielokrotnemu odbieraniu tej samej wiadomości.
        // Jednak lepszym i prostszym podejściem jest użycie innego mechanizmu niż player.
        // Najprościej jest po prostu zarejestrować listenera raz. Problem leżał gdzie indziej.
        // Poniższa rejestracja jest POPRAWNA.
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST) // Ustawiamy najwyższy priorytet
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // 🔥 POPRAWKA 2: Całkowicie anulujemy event, aby wiadomość [Not Secure] się nie pojawiała 🔥
        event.setCancelled(true);

        // Reszta logiki pozostaje taka sama
        String sectorName = plugin.getConfig().getString("this-sector-name", "Sektor");
        String formattedMessage = "§7[§b" + sectorName  + "§7] §f" + event.getPlayer().getName() + ": " + event.getMessage();

        sendToProxy(formattedMessage);
    }

    private void sendToProxy(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);

        // Znajdź pierwszego gracza online, aby wysłać przez niego wiadomość
        // To standardowa praktyka dla plugin messages, aby zapewnić, że jest "nadawca"
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender != null) {
            sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Ten listener jest wywoływany raz na serwer, więc problem duplikacji
        // związany z liczbą graczy nie powinien tu występować, jeśli kod jest poprawny.
        // Błąd [Not Secure] wskazuje, że event.setCancelled(true) nie działało jak powinno
        // z priorytetem LOWEST.
        if (!channel.equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        try {
            String msg = in.readUTF();
            // Rozgłaszamy wiadomość do wszystkich graczy na tym serwerze.
            // Ta metoda jest wywoływana tylko raz na serwer, a nie raz na gracza.
            Bukkit.broadcastMessage(msg);
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd przy odbieraniu wiadomości globalnej: " + e.getMessage());
        }
    }
}