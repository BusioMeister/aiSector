package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuildAdminCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final GuildManager guildManager;
    private final UserManager userManager;

    private static final String GLOBAL_CHANNEL = "global:chat";

    public GuildAdminCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("gildie.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "usun":
                handleDelete(sender, args);
                break;
            case "list":
            case "lista":
                handleList(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Gildie - komendy administracyjne:");
        sender.sendMessage(ChatColor.GRAY + "/ga list " + ChatColor.DARK_GRAY + "- lista wszystkich gildii");
        sender.sendMessage(ChatColor.GRAY + "/ga usun <tag> " + ChatColor.DARK_GRAY + "- usuń gildię po tagu");
    }

    // /ga list
    private void handleList(CommandSender sender) {
        Collection<Guild> guilds = guildManager.getAllGuilds();
        if (guilds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Nie ma żadnych gildii.");
            return;
        }

        String tags = guilds.stream()
                .map(Guild::getTag)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        sender.sendMessage(ChatColor.GOLD + "Gildie na serwerze (" + guilds.size() + "):");
        sender.sendMessage(ChatColor.YELLOW + tags);
    }

    // /ga usun <tag>
    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /ga usun <tag>");
            return;
        }

        String tag = args[1].toUpperCase();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            sender.sendMessage(ChatColor.RED + "Gildia o tagu [" + tag + "] nie istnieje.");
            return;
        }

        // wyczyść gildię wszystkim członkom ONLINE
        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                User u = userManager.loadOrGetUser(member);
                if (u != null) {
                    u.setGuildTag(null);
                    u.setGuildRole(null);
                }
                member.sendMessage(ChatColor.RED + "Twoja gildia [" + tag + "] została usunięta przez administrację.");
            }
        }

        guildManager.deleteGuild(guild);

        sender.sendMessage(ChatColor.GREEN + "Usunięto gildię [" + tag + "].");

        String msg = "§6[GILDIA] §eGildia §f[" + tag + "] §ezostała usunięta przez administrację.";
        sendGlobal(msg);
    }

    // ===== GLOBAL CHAT HELPER =====

    private void sendGlobal(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender != null) {
            sender.sendPluginMessage(plugin, GLOBAL_CHANNEL, out.toByteArray());
        }
    }
}
