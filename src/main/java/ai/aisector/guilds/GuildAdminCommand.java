package ai.aisector.guilds;

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
            sender.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        if (!guildManager.isReady()) {
            sender.sendMessage(ChatColor.GRAY + "Dane gildii są jeszcze ładowane. Spróbuj ponownie za chwilę.");
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
                    sender.sendMessage(ChatColor.RED + "Użycie: /ga fixuser <gracz>");
                    return true;
                }
                handleFixGuild(sender, args[1]);
                break;
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Gildie - komendy administracyjne:");
        sender.sendMessage(ChatColor.GRAY + "/ga list " + ChatColor.DARK_GRAY + "- lista wszystkich gildii");
        sender.sendMessage(ChatColor.GRAY + "/ga usun <tag> " + ChatColor.DARK_GRAY + "- usuń gildię po tagu");
        sender.sendMessage(ChatColor.GRAY + "/ga fixuser <gracz>" + ChatColor.DARK_GRAY + "- usuń gildię po tagu");
    }

    // /ga list
    private void handleList(CommandSender sender) {
        plugin.getMongoDBManager().findManyAsync("guilds", new Document())
                .whenComplete((documents, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null) {
                        sender.sendMessage(ChatColor.RED + "Nie udało się wczytać listy gildii.");
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
                        sender.sendMessage(ChatColor.YELLOW + "Nie ma żadnych gildii.");
                        return;
                    }
                    sender.sendMessage(ChatColor.GOLD + "Gildie w sieci (" + tags.size() + "):");
                    sender.sendMessage(ChatColor.YELLOW + String.join(", ", tags));
                }));
    }
    private void handleFixGuild(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("Ten gracz nie jest online.");
            return;
        }
        User user = plugin.getUserManager().loadOrGetUser(target);
        if (user == null) {
            sender.sendMessage("Nie udało się załadować danych gracza.");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName();
        userManager.updateGuildMembershipAsync(targetUuid, null, null)
                .whenComplete((ignored, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null) {
                        sender.sendMessage(ChatColor.RED + "Nie udało się zapisać zmian dla " + targetName + ".");
                        plugin.getLogger().warning("Could not persist guild fix for " + targetUuid + ": "
                                + error.getMessage());
                        return;
                    }
                    sender.sendMessage("Wyczyszczono dane gildii dla " + targetName + ".");
                    Player currentTarget = Bukkit.getPlayer(targetUuid);
                    if (currentTarget == target && target.isOnline()) {
                        currentTarget.sendMessage(ChatColor.RED
                                + "Twoje przypisanie do gildii zostało zresetowane przez administrację.");
                    }
                }));
    }

    // /ga usun <tag>
    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /ga usun <tag>");
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            sender.sendMessage(ChatColor.GRAY + "Wczytuję gildię [" + tag + "] z Mongo...");
            guildManager.reloadGuildAsync(tag).whenComplete((loaded, error) ->
                    BukkitTaskScheduler.execute(plugin, () -> {
                        if (sender instanceof Player player && !player.isOnline()) return;
                        if (error != null) {
                            sender.sendMessage(ChatColor.RED + "Nie udało się wczytać gildii [" + tag + "].");
                            plugin.getLogger().warning("Could not load guild " + tag + " for admin deletion: "
                                    + error.getMessage());
                        } else if (loaded == null) {
                            sender.sendMessage(ChatColor.RED + "Gildia o tagu [" + tag + "] nie istnieje.");
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
                        sender.sendMessage(ChatColor.RED + "Nie udało się bezpiecznie usunąć gildii. "
                                + "Odśwież dane i spróbuj ponownie.");
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
                        member.sendMessage(ChatColor.RED
                                + "Twoja gildia [" + tag + "] została usunięta przez administrację.");
                    }
                    sender.sendMessage(ChatColor.GREEN + "Usunięto gildię [" + tag + "].");
                    sendGlobal("§6[GILDIA] §eGildia §f[" + tag + "] §ezostała usunięta przez administrację.");
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
