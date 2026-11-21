package ai.aisector.guilds;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.user.UserManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.TreeSet;
import java.util.UUID;
import java.util.Locale;

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
            sender.sendMessage(Messages.text("guild-admin-command.brak-uprawnien", ChatColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        if (!guildManager.isReady()) {
            sender.sendMessage(Messages.text("guild-admin-command.dane-gildii-sa-jeszcze-ladowane-sprobuj-ponownie", ChatColor.GRAY));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
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
            case "fixuser":
                if (args.length < 2) {
                    sender.sendMessage(Messages.text("guild-admin-command.uzycie-ga-fixuser", ChatColor.RED));
                    return true;
                }
                handleFixGuild(sender, args[1]);
                break;
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.text("guild-admin-command.gildie-komendy-administracyjne", ChatColor.GOLD));
        sender.sendMessage(Messages.text("guild-admin-command.ga-list-lista-wszystkich-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        sender.sendMessage(Messages.text("guild-admin-command.ga-usun-usun-gildie-po-tagu", ChatColor.GRAY, ChatColor.DARK_GRAY));
        sender.sendMessage(Messages.text("guild-admin-command.ga-fixuser-usun-gildie-po-tagu", ChatColor.GRAY, ChatColor.DARK_GRAY));
    }

    // /ga list
    private void handleList(CommandSender sender) {
        plugin.getMongoDBManager().findManyAsync("guilds", new Document())
                .whenComplete((documents, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null) {
                        sender.sendMessage(Messages.text("guild-admin-command.nie-udalo-sie-wczytac-listy-gildii", ChatColor.RED));
                        plugin.getLogger().warning("Could not load guild list for admin: " + error.getMessage());
                        return;
                    }
                    TreeSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    for (Document document : documents == null ? java.util.List.<Document>of() : documents) {
                        if (document == null) continue;
                        try {
                        String tag = document.getString("tag");
                        if (tag != null && !tag.isBlank()) tags.add(tag);
                        } catch (RuntimeException malformed) {
                            plugin.getLogger().warning("Skipping malformed guild document in admin list");
                        }
                    }
                    if (tags.isEmpty()) {
                        sender.sendMessage(Messages.text("guild-admin-command.nie-ma-zadnych-gildii", ChatColor.YELLOW));
                        return;
                    }
                    sender.sendMessage(Messages.text("guild-admin-command.gildie-w-sieci", ChatColor.GOLD, tags.size()));
                    sender.sendMessage(ChatColor.YELLOW + String.join(", ", tags));
                }));
    }
    private void handleFixGuild(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Messages.text("guild-admin-command.ten-gracz-nie-jest-online"));
            return;
        }
        User user = plugin.getUserManager().loadOrGetUser(target);
        if (user == null) {
            sender.sendMessage(Messages.text("guild-admin-command.nie-udalo-sie-zaladowac-danych-gracza"));
            return;
        }

        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName();
        userManager.updateGuildMembershipAsync(targetUuid, null, null)
                .whenComplete((ignored, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null) {
                        sender.sendMessage(Messages.text("guild-admin-command.nie-udalo-sie-zapisac-zmian-dla", ChatColor.RED, targetName));
                        plugin.getLogger().warning("Could not persist guild fix for " + targetUuid + ": "
                                + error.getMessage());
                        return;
                    }
                    sender.sendMessage(Messages.text("guild-admin-command.wyczyszczono-dane-gildii-dla", targetName));
                    Player currentTarget = Bukkit.getPlayer(targetUuid);
                    if (currentTarget == target && target.isOnline()) {
                        currentTarget.sendMessage(Messages.text("guild-admin-command.twoje-przypisanie-do-gildii-zostalo-zresetowane-przez", ChatColor.RED));
                    }
                }));
    }

    // /ga usun <tag>
    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.text("guild-admin-command.uzycie-ga-usun", ChatColor.RED));
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            sender.sendMessage(Messages.text("guild-admin-command.wczytuje-gildie-z-mongo", ChatColor.GRAY, tag));
            guildManager.reloadGuildAsync(tag).whenComplete((loaded, error) ->
                    BukkitTaskScheduler.execute(plugin, () -> {
                        if (sender instanceof Player player && !player.isOnline()) return;
                        if (error != null) {
                            sender.sendMessage(Messages.text("guild-admin-command.nie-udalo-sie-wczytac-gildii", ChatColor.RED, tag));
                            plugin.getLogger().warning("Could not load guild " + tag + " for admin deletion: "
                                    + error.getMessage());
                        } else if (loaded == null) {
                            sender.sendMessage(Messages.text("guild-admin-command.gildia-o-tagu-nie-istnieje", ChatColor.RED, tag));
                        } else {
                            deleteLoadedGuild(sender, tag, loaded);
                        }
                    }));
            return;
        }

        deleteLoadedGuild(sender, tag, guild);
    }

    private void deleteLoadedGuild(CommandSender sender, String tag, Guild guild) {
        java.util.List<UUID> members = java.util.List.copyOf(guild.getMembers());
        guildManager.deleteGuildAsync(guild).whenComplete((deleted, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null || !Boolean.TRUE.equals(deleted)) {
                        sender.sendMessage(Messages.text("guild-admin-command.nie-udalo-sie-bezpiecznie-usunac-gildii-odswiez", ChatColor.RED));
                        if (error != null) {
                            plugin.getLogger().warning("Could not delete guild " + tag + ": " + error.getMessage());
                        }
                        return;
                    }

                    for (UUID memberId : members) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member == null) continue;
                        User user = userManager.loadOrGetUser(member);
                        if (user != null) {
                            user.setGuildTag(null);
                            user.setGuildRole(null);
                        }
                        member.sendMessage(Messages.text("guild-admin-command.twoja-gildia-zostala-usunieta-przez-administracje", ChatColor.RED, tag));
                    }
                    sender.sendMessage(Messages.text("guild-admin-command.usunieto-gildie", ChatColor.GREEN, tag));
                    sendGlobal(Messages.text("guild-admin-command.gildia-gildia-zostala-usunieta-przez-administracje", tag));
                }));
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
