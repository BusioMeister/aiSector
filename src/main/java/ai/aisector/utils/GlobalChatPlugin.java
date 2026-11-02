package ai.aisector.utils;

import ai.aisector.SectorPlugin;
import ai.aisector.guilds.Guild;
import ai.aisector.guilds.GuildManager;
import ai.aisector.user.User;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GlobalChatPlugin implements Listener, PluginMessageListener {

    private record AllyChatRoute(Guild guild, List<Guild> allies) { }
    private record GuildViewer(Player player, String guildTag) { }

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
    public void onPlayerChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String raw = UserTextSanitizer.sanitize(
                PlainTextComponentSerializer.plainText().serialize(event.message()));
        scheduleMainThread(() -> processChat(player, raw));
    }

    private void processChat(Player player, String raw) {
        if (!player.isOnline()) return;
        if (!GlobalChatPayloadValidation.validMessage(raw)) {
            player.sendMessage("§cWiadomość jest zbyt długa.");
            return;
        }
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
        User user = plugin.getUserManager().getUser(sender);
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
        User user = plugin.getUserManager().getUser(sender);
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
        if (!GlobalChatPayloadValidation.validMessage(message)) return;
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
        if (!GlobalChatPayloadValidation.validPacketLength(message)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        final String msg;
        try {
            msg = in.readUTF();
        } catch (Exception e) {
            return;
        }
        if (!GlobalChatPayloadValidation.validMessage(msg)) return;

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
        User senderUser = (senderOnline != null) ? plugin.getUserManager().getUser(senderOnline) : null;
        String senderTag = senderUser != null && senderUser.hasGuild() ? senderUser.getGuildTag() : null;
        List<GuildViewer> viewers = new ArrayList<>();
        Set<String> guildTags = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            User viewerUser = plugin.getUserManager().getUser(viewer);
            String viewerTag = viewerUser != null && viewerUser.hasGuild() ? viewerUser.getGuildTag() : null;
            viewers.add(new GuildViewer(viewer, viewerTag));
            if (viewerTag != null && !viewerTag.isBlank()) guildTags.add(viewerTag);
        }
        if (senderTag != null && !senderTag.isBlank()) guildTags.add(senderTag);

        GuildManager guildManager = plugin.getGuildManager();
        CompletableFuture<?>[] guildLoads = guildTags.stream()
                .map(guildManager::reloadGuildAsync)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(guildLoads).whenComplete((ignored, error) ->
                scheduleMainThread(() -> {
                    if (error != null) {
                        plugin.getLogger().warning("Could not refresh guild cache for global chat: " + error.getMessage());
                    }
                    for (GuildViewer viewer : viewers) {
                        if (!viewer.player().isOnline()) continue;
                        String guildPart = buildColoredGuildPart(viewer.guildTag(), senderTag);
                        String formatted = "§7[§b" + sectorName + "§7]" + guildPart
                                + " §f" + senderName + "§7: §f" + content;
                        viewer.player().sendMessage(formatted);
                    }
                }));
    }

    private String buildColoredGuildPart(String viewerTag, String senderTag) {
        if (senderTag == null || senderTag.isEmpty()) return "";

        // viewer bez gildii -> dla niego wszystko z gildii jest "obce" (czerwone)
        if (viewerTag == null || viewerTag.isBlank()) {
            return " §7[§c" + senderTag + "§7]";
        }

        if (viewerTag != null && viewerTag.equalsIgnoreCase(senderTag)) {
            return " §7[§a" + senderTag + "§7]"; // SAME (zielony)
        }

        // ALLY (niebieski)
        GuildManager gm = plugin.getGuildManager();
        Guild viewerGuild = gm.getGuildCached(viewerTag);
        if (viewerGuild != null
                && GuildManager.containsTag(viewerGuild.getAlliedGuilds(), senderTag)) {
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
        gm.reloadGuildAsync(tag).whenComplete((guild, error) -> scheduleMainThread(() -> {
            if (error != null) {
                plugin.getLogger().warning("Could not load guild " + tag + " for guild chat: " + error.getMessage());
                return;
            }
            if (guild != null) broadcastGuildChat(guild, tag, senderName, content);
        }));
    }

    private void handleIncomingAllyChat(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String tag = parts[0];
        String senderName = parts[1];
        String content = parts[2];

        GuildManager gm = plugin.getGuildManager();
        gm.reloadGuildAsync(tag).thenCompose(guild -> {
            if (guild == null) return CompletableFuture.completedFuture(null);
            List<CompletableFuture<Guild>> allyLoads = new ArrayList<>();
            for (String allyTag : List.copyOf(guild.getAlliedGuilds())) {
                allyLoads.add(gm.reloadGuildAsync(allyTag));
            }
            return CompletableFuture.allOf(allyLoads.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> new AllyChatRoute(guild,
                            allyLoads.stream().map(CompletableFuture::join).filter(java.util.Objects::nonNull).toList()));
        }).whenComplete((route, error) -> scheduleMainThread(() -> {
            if (error != null) {
                plugin.getLogger().warning("Could not load ally guilds for " + tag + ": " + error.getMessage());
                return;
            }
            if (route == null) return;
            String formatted = "§d[ALLY] §f[" + tag + "] §e" + senderName + "§7: §f" + content;
            broadcastToGuild(route.guild(), formatted);
            route.allies().forEach(ally -> broadcastToGuild(ally, formatted));
        }));
    }

    private void broadcastGuildChat(Guild guild, String tag, String senderName, String content) {
        String formatted = "§6[G] §f[" + tag + "] §e" + senderName + "§7: §f" + content;
        broadcastToGuild(guild, formatted);
    }

    private void broadcastToGuild(Guild guild, String message) {
        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) member.sendMessage(message);
        }
    }

    private void scheduleMainThread(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException ignoredShutdown) {
            // Async chat or Mongo completions can race Paper shutdown.
        }
    }
}
