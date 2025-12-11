package ai.aisector.utils;

import ai.aisector.SectorPlugin;
import ai.aisector.guilds.Guild;
import ai.aisector.guilds.GuildManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
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

import java.util.UUID;

public class GlobalChatPlugin implements Listener, PluginMessageListener {

    private final SectorPlugin plugin;
    private static final String CHANNEL = "global:chat";

    public GlobalChatPlugin(SectorPlugin plugin) {
        this.plugin = plugin;
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
        String raw = event.getMessage();

        if (raw.startsWith("!!")) {
            event.setCancelled(true);
            String content = raw.substring(2).trim();
            sendAllyChatToProxy(player, content);
            return;
        }
        if (raw.startsWith("!")) {
            event.setCancelled(true);
            String content = raw.substring(1).trim();
            sendGuildChatToProxy(player, content);
            return;
        }



        String sectorName = plugin.getConfig().getString("this-sector-name", "Sektor");

        // Tworzymy finalny format wiadomości z użyciem prefixu rangi
        String formattedMessage = "§7[§b" + sectorName  + "§7] " + player.getName() + "§f: " + event.getMessage();
        // --- KONIEC NOWEJ LOGIKI ---

        sendToProxy(formattedMessage);
    }
    private void sendGuildChatToProxy(Player sender, String content) {
        User user = plugin.getUserManager().loadOrGetUser(sender);
        if (user == null || !user.hasGuild()) {
            sender.sendMessage("§cNie jesteś w gildii.");
            return;
        }
        String tag = user.getGuildTag();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("[GCHAT]" + tag + "|" + sender.getName() + "|" + content);
        sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    private void sendAllyChatToProxy(Player sender, String content) {
        User user = plugin.getUserManager().loadOrGetUser(sender);
        if (user == null || !user.hasGuild()) {
            sender.sendMessage("§cNie jesteś w gildii.");
            return;
        }
        String tag = user.getGuildTag();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("[ALLYCHAT]" + tag + "|" + sender.getName() + "|" + content);
        sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }


    private void sendGuildChat(Player sender, String content) {
        GuildManager gm = plugin.getGuildManager();
        UserManager um = plugin.getUserManager();

        User user = um.loadOrGetUser(sender);
        if (user == null || !user.hasGuild()) {
            sender.sendMessage("§cNie jesteś w gildii.");
            return;
        }

        Guild guild = gm.getGuild(user.getGuildTag());
        if (guild == null) {
            sender.sendMessage("§cTwoja gildia nie istnieje.");
            return;
        }

        String formatted = "§6[G] §f[" + guild.getTag() + "] §e" + sender.getName() + "§7: §f" + content;

        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(formatted);
        }
    }

    private void sendAllyChat(Player sender, String content) {
        GuildManager gm = plugin.getGuildManager();
        UserManager um = plugin.getUserManager();

        User user = um.loadOrGetUser(sender);
        if (user == null || !user.hasGuild()) {
            sender.sendMessage("§cNie jesteś w gildii.");
            return;
        }

        Guild guild = gm.getGuild(user.getGuildTag());
        if (guild == null) {
            sender.sendMessage("§cTwoja gildia nie istnieje.");
            return;
        }

        String formatted = "§d[ALLY] §f[" + guild.getTag() + "] §e" + sender.getName() + "§7: §f" + content;

        // do własnej gildii
        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(formatted);
        }
        // do wszystkich sojuszy
        for (String allyTag : guild.getAlliedGuilds()) {
            Guild ally = gm.getGuild(allyTag);
            if (ally == null) continue;
            for (UUID memberId : ally.getMembers()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(formatted);
            }
        }
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

        // Mogą przychodzić też inne pakiety na tym kanale – zabezpieczamy się
        if (message == null || message.length < 2) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        final String msg;
        try {
            msg = in.readUTF();
        } catch (Exception e) {
            // To nie jest pakiet w formacie writeUTF – ignorujemy
            return;
        }

        // PRYWATNY CZAT GILDYJNY
        if (msg.startsWith("[GCHAT]")) {
            plugin.getLogger().info("[GlobalChatPlugin] recv from "
                    + (player != null ? player.getName() : "proxy")
                    + " msg=" + msg);

            handleIncomingGuildChat(msg.substring(7));
            return; // nic nie broadcastujemy globalnie
        }

        // PRYWATNY CZAT SOJUSZY
        if (msg.startsWith("[ALLYCHAT]")) {
            plugin.getLogger().info("[GlobalChatPlugin] recv from "
                    + (player != null ? player.getName() : "proxy")
                    + " msg=" + msg);

            handleIncomingAllyChat(msg.substring(10));
            return; // też bez broadcastu
        }

        // NORMALNY GLOBAL – tylko wiadomości bez prefiksów
        plugin.getLogger().info("[GlobalChatPlugin] recv from "
                + (player != null ? player.getName() : "proxy")
                + " msg=" + msg);

        Bukkit.broadcastMessage(msg);
    }

    // teraz używasz gm.getGuild(...)
// zmień na reloadGuild

    private void handleIncomingGuildChat(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String tag = parts[0];
        String senderName = parts[1];
        String content = parts[2];

        GuildManager gm = plugin.getGuildManager();          // <-- dodaj
        Guild guild = gm.reloadGuild(tag);                   // KLUCZ: reload
        if (guild == null) return;

        String formatted = "§6G §f[" + tag + "] §e" + senderName + "§7: §f" + content;

        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(formatted);
            }
        }
    }

    private void handleIncomingAllyChat(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String tag = parts[0];
        String senderName = parts[1];
        String content = parts[2];

        GuildManager gm = plugin.getGuildManager();
        Guild guild = gm.reloadGuild(tag);                   // też reload
        if (guild == null) return;

        String formatted = "§dALLY §f[" + tag + "] §e" + senderName + "§7: §f" + content;

        // własna gildia
        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(formatted);
        }

        // wszystkie sojusze
        for (String allyTag : guild.getAlliedGuilds()) {
            Guild ally = gm.reloadGuild(allyTag);            // KLUCZ: reload dla ally
            if (ally == null) continue;

            for (UUID memberId : ally.getMembers()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(formatted);
            }
        }
    }



}