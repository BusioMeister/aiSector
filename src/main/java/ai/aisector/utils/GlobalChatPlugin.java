package ai.aisector.utils;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class GlobalChatPlugin implements Listener, PluginMessageListener {

    private final SectorPlugin plugin;
    private final RankManager rankManager; // <-- DODAJEMY POLE
    private static final String CHANNEL = "global:chat";

    public GlobalChatPlugin(SectorPlugin plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager(); // <-- POBIERAMY RANK MANAGER
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Używamy priorytetu MONITOR, aby mieć pewność, że event nie jest już anulowany
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        // Anulujemy event, aby przejąć pełną kontrolę nad formatowaniem i wysyłką
        event.setCancelled(true);

        Player player = event.getPlayer();
        Rank playerRank = rankManager.getPlayerRank(player.getUniqueId());

        // --- POCZĄTEK NOWEJ LOGIKI ---
        String prefix = "";
        if (playerRank != null && playerRank.getPrefix() != null) {
            // Tłumaczymy kody kolorów (np. &c) na kolory w grze
            prefix = ChatColor.translateAlternateColorCodes('&', playerRank.getPrefix());
        }

        String sectorName = plugin.getConfig().getString("this-sector-name", "Sektor");

        // Tworzymy finalny format wiadomości z użyciem prefixu rangi
        String formattedMessage = "§7[§b" + sectorName  + "§7] " + prefix + player.getName() + "§f: " + event.getMessage();
        // --- KONIEC NOWEJ LOGIKI ---

        sendToProxy(formattedMessage);
    }

    private void sendToProxy(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);

        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender != null) {
            sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        try {
            String msg = in.readUTF();
            // Rozgłaszamy już sformatowaną wiadomość do wszystkich na tym serwerze
            Bukkit.broadcastMessage(msg);
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd przy odbieraniu wiadomości globalnej: " + e.getMessage());
        }
    }
}