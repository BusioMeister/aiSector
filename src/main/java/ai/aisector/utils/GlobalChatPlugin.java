package ai.aisector.utils;

import ai.aisector.SectorPlugin;
import ai.aisector.guilds.Guild;
import ai.aisector.guilds.GuildManager;
import ai.aisector.user.User;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String raw = event.getMessage();

        if (raw.startsWith("!!")) {
            String content = raw.substring(2).trim();
            sendAllyChatToProxy(player, content);
            return;
        }

        if (raw.startsWith("!")) {
            String content = raw.substring(1).trim();
            sendGuildChatToProxy(player, content);
            return;
        }

        // GLOBAL (per-odbiorca format na serwerach docelowych)
        String sectorName = plugin.getConfig().getString("this-sector-name", "Sektor");
        String payload = "[GLOBAL]" + sectorName + "|" + player.getName() + "|" + player.getUniqueId() + "|" + raw;
        sendToProxy(payload);
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

    private void sendToProxy(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);

        // plugin message musi wyjść "od gracza"
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender != null) {
            sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        if (message == null || message.length < 2) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        final String msg;
        try {
            msg = in.readUTF();
        } catch (Exception e) {
            return;
        }

        if (msg.startsWith("[GCHAT]")) {
            handleIncomingGuildChat(msg.substring(7));
            return;
        }

        if (msg.startsWith("[ALLYCHAT]")) {
            handleIncomingAllyChat(msg.substring(10));
            return;
        }

        if (msg.startsWith("[GLOBAL]")) {
            handleIncomingGlobal(msg.substring(8));
            return;
        }

        // fallback (gdyby przyszły stare wiadomości w starym formacie)
        Bukkit.broadcastMessage(msg);
    }

    private void handleIncomingGlobal(String payload) {
        // format: sector|senderName|senderUuid|content
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) return;

        String sectorName = parts[0];
        String senderName = parts[1];
        String senderUuidStr = parts[2];
        String content = parts[3];

        UUID senderUuid;
        try {
            senderUuid = UUID.fromString(senderUuidStr);
        } catch (Exception e) {
            return;
        }

        Player senderOnline = Bukkit.getPlayer(senderUuid);
        User senderUser = (senderOnline != null) ? plugin.getUserManager().loadOrGetUser(senderOnline) : null;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            User viewerUser = plugin.getUserManager().loadOrGetUser(viewer);

            String guildPart = buildColoredGuildPart(viewerUser, senderUser);
            String formatted = "§7[§b" + sectorName + "§7]" + guildPart + " §f" + senderName + "§7: §f" + content;

            viewer.sendMessage(formatted);
        }
    }

    private String buildColoredGuildPart(User viewerUser, User senderUser) {
        if (senderUser == null || !senderUser.hasGuild()) return "";

        String senderTag = senderUser.getGuildTag();
        if (senderTag == null || senderTag.isEmpty()) return "";

        // viewer bez gildii -> dla niego wszystko z gildii jest "obce" (czerwone)
        if (viewerUser == null || !viewerUser.hasGuild()) {
            return " §7[§c" + senderTag + "§7]";
        }

        String viewerTag = viewerUser.getGuildTag();
        if (viewerTag != null && viewerTag.equalsIgnoreCase(senderTag)) {
            return " §7[§a" + senderTag + "§7]"; // SAME (zielony)
        }

        // ALLY (niebieski)
        GuildManager gm = plugin.getGuildManager();
        Guild viewerGuild = gm.reloadGuild(viewerTag);
        if (viewerGuild != null && viewerGuild.getAlliedGuilds() != null
                && viewerGuild.getAlliedGuilds().contains(senderTag)) {
            return " §7[§9" + senderTag + "§7]";
        }

        // ENEMY (czerwony)
        return " §7[§c" + senderTag + "§7]";
    }

    private void handleIncomingGuildChat(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String tag = parts[0];
        String senderName = parts[1];
        String content = parts[2];

        GuildManager gm = plugin.getGuildManager();
        Guild guild = gm.reloadGuild(tag);
        if (guild == null) return;

        String formatted = "§6[G] §f[" + tag + "] §e" + senderName + "§7: §f" + content;
        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(formatted);
        }
    }

    private void handleIncomingAllyChat(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String tag = parts[0];
        String senderName = parts[1];
        String content = parts[2];

        GuildManager gm = plugin.getGuildManager();
        Guild guild = gm.reloadGuild(tag);
        if (guild == null) return;

        String formatted = "§d[ALLY] §f[" + tag + "] §e" + senderName + "§7: §f" + content;

        // własna gildia
        for (UUID memberId : guild.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(formatted);
        }

        // sojusze
        for (String allyTag : guild.getAlliedGuilds()) {
            Guild ally = gm.reloadGuild(allyTag);
            if (ally == null) continue;

            for (UUID memberId : ally.getMembers()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(formatted);
            }
        }
    }
}
