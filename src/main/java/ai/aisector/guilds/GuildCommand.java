package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.StoredHomeData;
import ai.aisector.user.UserManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GuildCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final GuildManager guildManager;
    private final UserManager userManager;
    private final Set<UUID> pendingGuildSyncs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingGuildCreations = ConcurrentHashMap.newKeySet();
    private static final String GLOBAL_CHANNEL = "global:chat";

    public GuildCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }
    private void sendGlobal(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender != null) {
            sender.sendPluginMessage(plugin, GLOBAL_CHANNEL, out.toByteArray());
        }
    }

    private CompletableFuture<Void> persistGuilds(Player actor, Guild... guilds) {
        UUID actorUuid = actor.getUniqueId();
        CompletableFuture<?>[] writes = new CompletableFuture<?>[guilds.length];
        int index = 0;
        for (Guild guild : guilds) {
            String tag = guild.getTag();
            UUID ownerUuid = guild.getOwner();
            CompletableFuture<Void> write = guildManager.saveGuild(guild);
            writes[index++] = write;
            write.whenComplete((ignored, error) -> {
                if (error == null || actorUuid.equals(ownerUuid)) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player currentActor = Bukkit.getPlayer(actorUuid);
                        if (currentActor != null) {
                            currentActor.sendMessage(ChatColor.RED + "Zmiany gildii [" + tag
                                    + "] nie zostały zapisane. Cache został odświeżony; ponów operację.");
                        }
                    });
                } catch (RuntimeException ignoredShutdown) {
                    plugin.getLogger().warning("Guild command save failed during shutdown for " + tag + ": "
                            + error.getMessage());
                }
            });
        }
        return CompletableFuture.allOf(writes);
    }

    private void persistGuildsThen(Player actor, Consumer<Player> onSaved, Guild... guilds) {
        UUID actorUuid = actor.getUniqueId();
        persistGuilds(actor, guilds).whenComplete((ignored, error) -> {
            if (error != null) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player currentActor = Bukkit.getPlayer(actorUuid);
                    if (currentActor == actor && actor.isOnline()) onSaved.accept(currentActor);
                });
            } catch (RuntimeException ignoredShutdown) {
                plugin.getLogger().warning("Could not finish a guild command during shutdown for " + actorUuid);
            }
        });
    }

    private void persistGuildMemberships(Player actor, Guild guild, Map<UUID, String> memberRoles,
                                         Consumer<Player> onSuccess) {
        UUID actorUuid = actor.getUniqueId();
        persistGuilds(actor, guild).whenComplete((ignored, saveError) -> {
            if (saveError != null) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CompletableFuture<?>[] profileWrites = memberRoles.entrySet().stream()
                            .map(entry -> {
                                boolean removeMembership = "NONE".equals(entry.getValue());
                                return userManager.updateGuildMembershipAsync(entry.getKey(),
                                        removeMembership ? null : guild.getTag(),
                                        removeMembership ? null : entry.getValue(), removeMembership ? guild.getTag() : null);
                            })
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(profileWrites).whenComplete((profilesSaved, profileError) -> {
                        try {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player currentActor = Bukkit.getPlayer(actorUuid);
                                if (profileError != null) {
                                    plugin.getLogger().severe("Guild " + guild.getTag()
                                            + " was saved but member profiles could not be updated: "
                                            + profileError.getMessage());
                                    if (currentActor == actor && actor.isOnline()) {
                                        currentActor.sendMessage(ChatColor.RED
                                                + "Gildia została zapisana, ale profili członków nie udało się odświeżyć.");
                                    }
                                    return;
                                }
                                if (currentActor == actor && actor.isOnline()) onSuccess.accept(currentActor);
                            });
                        } catch (RuntimeException ignoredShutdown) {
                            plugin.getLogger().warning("Could not complete guild membership update during shutdown for "
                                    + guild.getTag());
                        }
                    });
                });
            } catch (RuntimeException ignoredShutdown) {
                plugin.getLogger().warning("Could not queue guild membership update during shutdown for "
                        + guild.getTag());
            }
        });
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda tylko dla graczy.");
            return true;
        }

        Player player = (Player) sender;
        if (!guildManager.isReady()) {
            player.sendMessage(ChatColor.GRAY + "Dane gildii są jeszcze ładowane. Spróbuj ponownie za chwilę.");
            return true;
        }
        User user = userManager.loadOrGetUser(player);
        if (user == null) {
            player.sendMessage(ChatColor.RED + "Nie udało się załadować danych gracza.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        if (user.hasGuild() && guildManager.getGuildCached(user.getGuildTag()) == null) {
            UUID playerUuid = player.getUniqueId();
            if (!pendingGuildSyncs.add(playerUuid)) {
                player.sendMessage(ChatColor.GRAY + "Synchronizuję dane gildii. Spróbuj ponownie za chwilę.");
                return true;
            }

            String guildTag = user.getGuildTag();
            player.sendMessage(ChatColor.GRAY + "Synchronizuję dane gildii...");
            guildManager.reloadGuildAsync(guildTag).whenComplete((guild, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        pendingGuildSyncs.remove(playerUuid);
                        Player currentPlayer = Bukkit.getPlayer(playerUuid);
                        if (currentPlayer != player || !player.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(ChatColor.RED
                                    + "Nie udało się zsynchronizować gildii. Spróbuj ponownie.");
                            plugin.getLogger().warning("Could not synchronize guild " + guildTag + " for "
                                    + playerUuid + ": " + error.getMessage());
                            return;
                        }

                        User currentUser = userManager.getUser(playerUuid);
                        if (currentUser == null) {
                            currentPlayer.sendMessage(ChatColor.RED + "Dane gracza są nadal ładowane.");
                            return;
                        }
                        if (guild == null) {
                            currentUser.setGuildTag(null);
                            currentUser.setGuildRole(null);
                        }
                        onCommand(currentPlayer, cmd, label, args.clone());
                    });
                if (!scheduled) pendingGuildSyncs.remove(playerUuid);
            });
            return true;
        }

        // A reused tag or refreshed guild cache must not inherit a stale profile's OWNER/MOD role.
        if (user.hasGuild()) {
            if (guildManager.isPersistencePending(user.getGuildTag())) {
                player.sendMessage(ChatColor.GRAY + "Zmiany gildii są jeszcze zapisywane. Spróbuj za chwilę.");
                return true;
            }
            Guild current = guildManager.getGuildCached(user.getGuildTag());
            String role = current == null ? null : current.roleOf(player.getUniqueId());
            user.setGuildTag(role == null ? null : current.getTag());
            user.setGuildRole(role);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String requestedGuildTag = guildTagArgument(sub, args);
        if (requestedGuildTag != null && guildManager.getGuildCached(requestedGuildTag) == null) {
            UUID playerUuid = player.getUniqueId();
            if (!pendingGuildSyncs.add(playerUuid)) {
                player.sendMessage(ChatColor.GRAY + "Synchronizuję dane gildii. Spróbuj ponownie za chwilę.");
                return true;
            }
            player.sendMessage(ChatColor.GRAY + "Wczytuję gildię [" + requestedGuildTag + "] z Mongo...");
            guildManager.reloadGuildAsync(requestedGuildTag).whenComplete((loadedGuild, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        pendingGuildSyncs.remove(playerUuid);
                        Player currentPlayer = Bukkit.getPlayer(playerUuid);
                        if (currentPlayer != player || !player.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(ChatColor.RED + "Nie udało się wczytać gildii. Spróbuj ponownie.");
                            plugin.getLogger().warning("Could not load requested guild " + requestedGuildTag + ": "
                                    + error.getMessage());
                        } else if (loadedGuild == null) {
                            currentPlayer.sendMessage(ChatColor.RED + "Gildia o tagu [" + requestedGuildTag
                                    + "] nie istnieje.");
                        } else {
                            onCommand(currentPlayer, cmd, label, args.clone());
                        }
                    });
                if (!scheduled) pendingGuildSyncs.remove(playerUuid);
            });
            return true;
        }

        switch (sub) {
            case "zaloz":
            case "create":
                handleCreate(player, user, args);
                break;
            case "mistrz":
                handleMistrz(player, user, args);
                break;
            case "wyrzuc":
                handleKick(player, user, args);
                break;

            case "sojusz":
            case "ally":
                handleAlly(player, user, args);
                break;

            case "przekaz":
                handlePrzekaz(player, user, args);
                break;
            case "usun":
                handleUsun(player, user);
                break;
            case "ustawdom":
                handleSetHomeGuild(player, user);
                break;
            case "dom":
            case "baza":
                handleGuildHome(player, user);
                break;
            case "pvp":
                handlePvpToggle(player, user);
                break;
            case "pvpsojusz":
                handlePvpAlliesToggle(player, user);
                break;
            case "zastepca":
                handleDeputy(player, user, args);
                break;
            case "zapros":
            case "invite":
                handleInvite(player, user, args);
                break;
            case "alertchat":
                handleGuildWelcome(player, user, args);
                break;
            case "akceptuj":
            case "accept":
                handleAccept(player, user, args);
                break;
            case "alert":
                handleGuildAlert(player, user, args);
                break;
            case "dolacz":
            case "join":
                handleJoin(player, user, args);
                break;
            case "opusc":
            case "leave":
                handleLeave(player, user);
                break;
            case "info":
                handleInfo(player, user, args);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private String guildTagArgument(String subcommand, String[] args) {
        if (args.length < 2) return null;
        return switch (subcommand) {
            case "dolacz", "join", "akceptuj", "accept", "sojusz", "ally" ->
                    GuildManager.normalizeTag(args[1]);
            case "info" -> GuildManager.normalizeTag(args[1]);
            default -> null;
        };
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.YELLOW + "Gildia - komendy:");
        p.sendMessage(ChatColor.GRAY + "/g zaloz <tag> <nazwa> " + ChatColor.DARK_GRAY + "- tworzy nową gildię");
        p.sendMessage(ChatColor.GRAY + "/g dolacz <tag> " + ChatColor.DARK_GRAY + "- dołącz do gildii");
        p.sendMessage(ChatColor.GRAY + "/g opusc " + ChatColor.DARK_GRAY + "- opuść swoją gildię");
        p.sendMessage(ChatColor.GRAY + "/g info [tag] " + ChatColor.DARK_GRAY + "- informacje o gildii");
        p.sendMessage(ChatColor.GRAY + "/g zapros <gracz> " + ChatColor.DARK_GRAY + "- zaproś gracza do gildii");
        p.sendMessage(ChatColor.GRAY + "/g akceptuj <tag> " + ChatColor.DARK_GRAY + "- zaakceptuj zaproszenie");
        p.sendMessage(ChatColor.GRAY + "/g mistrz <gracz> " + ChatColor.DARK_GRAY + "- nadaj/odbierz rangę mistrza (mod)");
        p.sendMessage(ChatColor.GRAY + "/g przekaz <gracz> " + ChatColor.DARK_GRAY + "- przekaż lidera gildii");
        p.sendMessage(ChatColor.GRAY + "/g usun " + ChatColor.DARK_GRAY + "- rozwiąż swoją gildię");
        p.sendMessage(ChatColor.GRAY + "/g sojusz <tag> " + ChatColor.DARK_GRAY + "- zawrzyj lub zerwij sojusz z gildią");
        p.sendMessage(ChatColor.GRAY + "/g wyrzuc <gracz> " + ChatColor.DARK_GRAY + "- wyrzuć gracza z gildii");
        p.sendMessage(ChatColor.GRAY + "/g ustawdom " + ChatColor.DARK_GRAY + "- ustaw dom gildii");
        p.sendMessage(ChatColor.GRAY + "/g dom " + ChatColor.DARK_GRAY + "- teleport do domu gildii");
        p.sendMessage(ChatColor.GRAY + "/g zastepca <gracz> " + ChatColor.DARK_GRAY + "- nadaj/odbierz rangę zastępcy");
        p.sendMessage(ChatColor.GRAY + "/g pvp " + ChatColor.DARK_GRAY + "- włącz/wyłącz pvp w gildii");
        p.sendMessage(ChatColor.GRAY + "/g pvpsojusz " + ChatColor.DARK_GRAY + "- włącz/wyłącz pvp z sojuszami");
        p.sendMessage(ChatColor.GRAY + "/g alert <treść> " + ChatColor.DARK_GRAY + "- wysyła jednorazowy alert na ekran do gildii");
        p.sendMessage(ChatColor.GRAY + "/g alertchat <treść/off> " + ChatColor.DARK_GRAY + "- ustawia powitanie dla gildii");


    }
    // /g alertchat TREŚĆ | off
    private void handleGuildWelcome(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!hasGuildManagePerm(user)) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub mistrz może zmieniać powitanie.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g alertchat <treść/off>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        if (args[1].equalsIgnoreCase("off")) {
            guild.setWelcomeMessage(null);
            persistGuildsThen(p, actor ->
                    actor.sendMessage(ChatColor.YELLOW + "Powitanie gildii zostało wyłączone."), guild);
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.length() > 256) {
            p.sendMessage(ChatColor.RED + "Powitanie może mieć maksymalnie 256 znaków.");
            return;
        }
        guild.setWelcomeMessage(message);
        persistGuildsThen(p, actor -> {
            String formatted = "§6[GILDIA] §f[" + tag + "] §e" + message;
            for (UUID memberId : guild.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) member.sendMessage(formatted);
            }
            actor.sendMessage(ChatColor.GREEN + "Ustawiono powitanie gildii.");
        }, guild);
    }

    // /g alert TREŚĆ
    private void handleGuildAlert(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!hasGuildManagePerm(user)) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub mistrz może wysyłać alerty.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g alert <treść>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.length() > 256) {
            p.sendMessage(ChatColor.RED + "Alert może mieć maksymalnie 256 znaków.");
            return;
        }

        net.kyori.adventure.title.Title.Times times =
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2000),
                        java.time.Duration.ofMillis(300));

        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                Component.text("[" + tag + "]", NamedTextColor.GOLD),
                Component.text(message, NamedTextColor.YELLOW),
                times
        );

        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.showTitle(title);
            }
        }

        p.sendMessage(ChatColor.GREEN + "Wysłano alert do gildii.");
    }

    private boolean hasGuildManagePerm(User user) {
        String role = user.getGuildRole();
        return "OWNER".equalsIgnoreCase(role) || "MOD".equalsIgnoreCase(role);
    }

    // /g pvp
    private void handlePvpToggle(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!hasGuildManagePerm(user)) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub mistrz może zmieniać ustawienia PvP.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        boolean newValue = !guild.isFriendlyFireGuild();
        guild.setFriendlyFireGuild(newValue);
        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(tag);
            actor.sendMessage(ChatColor.YELLOW + "PvP wewnątrz gildii " +
                    (newValue ? ChatColor.RED + "WŁĄCZONE" : ChatColor.GREEN + "WYŁĄCZONE") + ChatColor.YELLOW + ".");
        }, guild);
    }

    private void handlePvpAlliesToggle(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!hasGuildManagePerm(user)) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub mistrz może zmieniać ustawienia PvP.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        boolean newValue = !guild.isFriendlyFireAllies();
        guild.setFriendlyFireAllies(newValue);
        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(tag);
            actor.sendMessage(ChatColor.YELLOW + "PvP z sojuszami " +
                    (newValue ? ChatColor.RED + "WŁĄCZONE" : ChatColor.GREEN + "WYŁĄCZONE") + ChatColor.YELLOW + ". "
                    + ChatColor.GRAY + "Musi być tak samo ustawione w obu gildiach.");
        }, guild);
    }


    // /g zastepca GRACZ  -> toggle deputy
    private void handleDeputy(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider może ustawić zastępcę.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g zastepca <gracz>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest online.");
            return;
        }
        if (!guild.getMembers().contains(target.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest w Twojej gildii.");
            return;
        }
        if (guild.getOwner() != null && guild.getOwner().equals(target.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Lider nie może być zastępcą.");
            return;
        }

        UUID uuid = target.getUniqueId();
        if (guild.getDeputy() != null && guild.getDeputy().equals(uuid)) {
            guild.setDeputy(null);
            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(ChatColor.YELLOW + "Zdjąłeś rangę zastępcy z " + targetName + ".");
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(ChatColor.RED + "Straciłeś rangę zastępcy w gildii " + tag + ".");
                }
            }, guild);
        } else {
            guild.setDeputy(uuid);
            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(ChatColor.GREEN + "Nadałeś rangę zastępcy graczowi " + targetName + ".");
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(ChatColor.GREEN + "Otrzymałeś rangę zastępcy w gildii " + tag + ".");
                }
            }, guild);
        }
    }

    private void handleMistrz(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider gildii może nadawać rangę mistrza.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g mistrz <gracz>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest online.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Nie możesz nadać sobie rangi mistrza.");
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest w Twojej gildii.");
            return;
        }

        UUID uuid = target.getUniqueId();
        if (guild.getMods().contains(uuid)) {
            guild.getMods().remove(uuid);
            persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(ChatColor.YELLOW + "Zabrałeś rangę mistrza graczowi " + targetName + ".");
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(ChatColor.RED + "Straciłeś rangę mistrza w gildii " + tag + ".");
                }
            });
        } else {
            guild.getMods().add(uuid);
            persistGuildMemberships(p, guild, Map.of(uuid, "MOD"), actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(ChatColor.GREEN + "Nadałeś rangę mistrza graczowi " + targetName + ".");
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(ChatColor.GREEN + "Otrzymałeś rangę mistrza w gildii " + tag + ".");
                }
            });
        }
    }

    private void handleKick(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        String role = user.getGuildRole();
        if (!"OWNER".equalsIgnoreCase(role) && !"MOD".equalsIgnoreCase(role)) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub mistrz może wyrzucać graczy z gildii.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g wyrzuc <gracz>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest online.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Nie możesz wyrzucić samego siebie. Użyj /g opusc.");
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest w Twojej gildii.");
            return;
        }
        UUID uuid = target.getUniqueId();
        if (uuid.equals(guild.getOwner())) {
            p.sendMessage(ChatColor.RED + "Nie możesz wyrzucić lidera gildii.");
            return;
        }

        guild.getMembers().remove(uuid);
        guild.getMods().remove(uuid);
        String kickedName = target.getName();
        persistGuildMemberships(p, guild, Map.of(uuid, "NONE"), actor -> {
            guildManager.reloadGuild(tag);
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(ChatColor.GREEN + "Wyrzuciłeś gracza " + kickedName + " z gildii.");
            Player currentTarget = Bukkit.getPlayer(uuid);
            if (currentTarget != null) {
                currentTarget.sendMessage(ChatColor.RED + "Zostałeś wyrzucony z gildii " + tag + ".");
            }
            sendGlobal("§6[GILDIA] §eGracz §f" + kickedName + " §ezostał wyrzucony z gildii §f[" + tag + "]§e.");
        });
    }

    private void handlePrzekaz(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider może przekazać gildię.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g przekaz <gracz>");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest online.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Już jesteś liderem.");
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest w Twojej gildii.");
            return;
        }

        guild.setOwner(target.getUniqueId());
        guild.getMods().remove(target.getUniqueId());

        UUID currentOwnerUuid = p.getUniqueId();
        UUID newOwnerUuid = target.getUniqueId();
        persistGuildMemberships(p, guild, Map.of(currentOwnerUuid, "MEMBER", newOwnerUuid, "OWNER"), actor -> {
            guildManager.reloadGuild(tag);
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(ChatColor.GREEN + "Przekazałeś przywództwo gildii graczowi " + targetName + ".");
            Player newOwner = Bukkit.getPlayer(newOwnerUuid);
            if (newOwner != null) newOwner.sendMessage(ChatColor.GREEN + "Zostałeś nowym liderem gildii " + tag + ".");
        });
    }

    // /g ustawdom
    private void handleSetHomeGuild(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider może ustawić dom gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // świeża gildia
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        Location loc = p.getLocation();
        String sector = plugin.getSectorManager().getSectorForLocation(loc.getBlockX(), loc.getBlockZ());

        guild.setHomeSector(sector);
        guild.setHomeWorld(loc.getWorld().getName());
        guild.setHomeX(loc.getX());
        guild.setHomeY(loc.getY());
        guild.setHomeZ(loc.getZ());
        guild.setHomeYaw(loc.getYaw());
        guild.setHomePitch(loc.getPitch());

        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(tag);
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(ChatColor.GREEN + "Ustawiono dom gildii w tym miejscu.");
        }, guild);
    }

    private void handleGuildHome(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // zawsze najnowsze dane
        plugin.getGuildTagManager().refreshAllOnline();

        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }
        if (guild.getHomeSector() == null || guild.getHomeWorld() == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie ma ustawionego domu. Użyj /g ustawdom.");
            return;
        }

        try {
            StoredHomeData.validateLocation(guild.getHomeWorld(), guild.getHomeX(), guild.getHomeY(),
                    guild.getHomeZ(), guild.getHomeYaw(), guild.getHomePitch());
        } catch (IllegalArgumentException invalidHome) {
            p.sendMessage(ChatColor.RED + "Zapis domu gildii jest nieprawidłowy.");
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("world", guild.getHomeWorld());
        json.addProperty("x", guild.getHomeX());
        json.addProperty("y", guild.getHomeY());
        json.addProperty("z", guild.getHomeZ());
        json.addProperty("yaw", guild.getHomeYaw());
        json.addProperty("pitch", guild.getHomePitch());

        p.sendMessage(ChatColor.YELLOW + "Rozpoczynam teleportację do domu gildii... Nie ruszaj się przez 5 sekund.");
        new ai.aisector.task.GuildHomeTeleportWarmupTask(p, guild.getHomeSector(), json, plugin).start();
    }
    // /g sojusz <tag>
// 1) jeśli jest już sojusz -> zerwij
// 2) jeśli mamy zaproszenie od tej gildii -> akceptuj (zawrzyj sojusz)
// 3) w przeciwnym wypadku -> wyślij zaproszenie (title + ewentualny global)
    private void handleAlly(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider gildii może zarządzać sojuszami.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /g sojusz <tag>");
            return;
        }

        String myTag = user.getGuildTag();
        // zawsze świeża wersja z Mongo (ważne między sektorami)
        Guild myGuild = guildManager.reloadGuild(myTag);
        if (myGuild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String otherTag = GuildManager.normalizeTag(args[1]);
        if (myTag.equalsIgnoreCase(otherTag)) {
            p.sendMessage(ChatColor.RED + "Nie możesz zrobić sojuszu z własną gildią.");
            return;
        }

        // też ładujemy z bazy, żeby widzieć gildię z innego sektora
        Guild otherGuild = guildManager.reloadGuild(otherTag);
        if (otherGuild == null) {
            p.sendMessage(ChatColor.RED + "Gildia o tagu [" + otherTag + "] nie istnieje.");
            return;
        }

        // 1) jeśli już jest sojusz -> ZERWIJ
        if (GuildManager.containsTag(myGuild.getAlliedGuilds(), otherTag)) {
            myGuild.getAlliedGuilds().remove(otherTag);
            otherGuild.getAlliedGuilds().remove(myTag);

            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(myTag);
                guildManager.reloadGuild(otherTag);
                plugin.getGuildTagManager().refreshAllOnline();
                sendGlobal("§6[GILDIA] §eSojusz między gildiami §f[" + myTag + "] §ea §f["
                        + otherTag + "] §ezostał zerwany.");
                actor.sendMessage(ChatColor.GREEN + "Zerwano sojusz z gildią [" + otherTag + "].");
            }, myGuild, otherGuild);
            return;
        }

        // 2) jeśli mamy zaproszenie od tej gildii -> AKCEPTUJ
        // (ktoś wcześniej zrobił /g sojusz <naszTag> i dodał myTag do allyInvitations)
        if (myGuild.getAllyInvitations().contains(otherTag)) {
            myGuild.getAllyInvitations().remove(otherTag);
            myGuild.getAlliedGuilds().add(otherTag);
            otherGuild.getAlliedGuilds().add(myTag);

            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(myTag);
                guildManager.reloadGuild(otherTag);
                plugin.getGuildTagManager().refreshAllOnline();
                sendGlobal("§6[GILDIA] §eGildie §f[" + myTag + "] §eoraz §f[" + otherTag
                        + "] §ezawarły sojusz.");
                actor.sendMessage(ChatColor.GREEN + "Zawarto sojusz z gildią [" + otherTag + "].");
            }, myGuild, otherGuild);
            return;
        }

        // 3) w przeciwnym wypadku -> WYSYŁAMY ZAPROSZENIE
        // zapisujemy zaproszenie po stronie drugiej gildii
        otherGuild.getAllyInvitations().add(myTag);
        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(otherTag);
            for (UUID memberId : otherGuild.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendTitle(
                            ChatColor.GOLD + "Sojusz od [" + myTag + "]",
                            ChatColor.YELLOW + "Lider: /g sojusz " + myTag,
                            10, 60, 10
                    );
                }
            }
            sendGlobal("§6[GILDIA] §f[" + myTag + "] §ewysłała propozycję sojuszu do gildii §f["
                    + otherTag + "]§e.");
            actor.sendMessage(ChatColor.GREEN + "Wysłałeś propozycję sojuszu do gildii [" + otherTag + "].");
        }, otherGuild);
    }




    // /g rozwiaz
    private void handleUsun(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider może rozwiązać gildię.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            user.setGuildTag(null);
            user.setGuildRole(null);
            p.sendMessage(ChatColor.RED + "Gildia już nie istnieje, dane wyczyszczono.");
            return;
        }
        UUID ownerUuid = p.getUniqueId();
        String guildName = guild.getName();
        List<UUID> members = new ArrayList<>(guild.getMembers());
        p.sendMessage(ChatColor.GRAY + "Rozwiązuję gildię...");
        guildManager.deleteGuildAsync(guild).whenComplete((deleted, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    Player owner = Bukkit.getPlayer(ownerUuid);
                    if (error != null || !Boolean.TRUE.equals(deleted)) {
                        if (owner != null) {
                            owner.sendMessage(ChatColor.RED
                                    + "Nie udało się bezpiecznie rozwiązać gildii. Spróbuj ponownie za chwilę.");
                        }
                        if (error != null) {
                            plugin.getLogger().warning("Could not dissolve guild " + tag + ": "
                                    + error.getMessage());
                        }
                        return;
                    }

                    for (UUID memberId : members) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member == null) continue;
                        User memberUser = userManager.getUser(memberId);
                        if (memberUser != null) {
                            memberUser.setGuildTag(null);
                            memberUser.setGuildRole(null);
                        }
                        if (!memberId.equals(ownerUuid)) {
                            member.sendMessage(ChatColor.RED + "Twoja gildia [" + tag + "] została rozwiązana.");
                        }
                    }
                    User currentOwnerUser = userManager.getUser(ownerUuid);
                    if (currentOwnerUser != null) {
                        currentOwnerUser.setGuildTag(null);
                        currentOwnerUser.setGuildRole(null);
                    }
                    plugin.getGuildTagManager().refreshAllOnline();
                    if (owner != null) {
                        plugin.getScoreboardManager().updateBoard(owner);
                        owner.sendMessage(ChatColor.YELLOW + "Rozwiązałeś gildię [" + tag + "] " + guildName + ".");
                    }
                    sendGlobal("§6[GILDIA] §eGildia §f[" + tag + "] §e" + guildName + " została rozwiązana.");
                }));
    }

    // /gildia zapros GRACZ
    private void handleInvite(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())
                && !"MOD".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider lub moderator może zapraszać.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia zapros <gracz>");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "Ten gracz nie jest online.");
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null) {
            p.sendMessage(ChatColor.RED + "Nie udało się załadować danych tego gracza.");
            return;
        }
        if (targetUser.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Ten gracz już jest w gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        guild.getInvitations().add(targetUuid);
        persistGuildsThen(p, actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(ChatColor.GREEN + "Wysłałeś zaproszenie do gildii [" + tag + "] dla " + targetName + ".");
            Player currentTarget = Bukkit.getPlayer(targetUuid);
            if (currentTarget != null) {
                currentTarget.sendMessage(ChatColor.YELLOW + "Otrzymałeś zaproszenie do gildii [" + tag + "] "
                        + guild.getName() + ". Użyj /gildia akceptuj " + tag + " aby dołączyć.");
            }
        }, guild);
    }
    // /gildia akceptuj TAG
    private void handleAccept(Player p, User user, String[] args) {
        if (user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Najpierw opuść obecną gildię.");
            return;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia akceptuj <tag>");
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Taka gildia nie istnieje.");
            return;
        }

        UUID uuid = p.getUniqueId();
        if (!guild.getInvitations().contains(uuid)) {
            p.sendMessage(ChatColor.RED + "Nie masz zaproszenia do tej gildii.");
            return;
        }

        guild.getInvitations().remove(uuid);
        guild.getMembers().add(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(ChatColor.GREEN + "Dołączyłeś do gildii [" + tag + "] " + guild.getName() + ".");
        });
    }


    // /gildia stworz TAG NAZWA...
    private void handleCreate(Player p, User user, String[] args) {
        if (user.hasGuild()) {
            Guild guild = guildManager.getGuild(user.getGuildTag());
            if (guild == null) {
                // gildia zniknęła z bazy -> czyścimy usera
                user.setGuildTag(null);
                user.setGuildRole(null);
            }
        }
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia zaloz <tag> <nazwa>");
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        if (tag == null || !tag.matches("[A-Z0-9]{2,5}")) {
            p.sendMessage(ChatColor.RED + "Tag musi mieć od 2 do 5 liter lub cyfr.");
            return;
        }
        if (name.isBlank() || name.length() > 32) {
            p.sendMessage(ChatColor.RED + "Nazwa gildii musi mieć od 1 do 32 znaków.");
            return;
        }
        Location loc = p.getLocation();
        Guild guild = new Guild(tag, name, p.getUniqueId());
        guild.getMembers().add(p.getUniqueId());
        guild.setHomeWorld(loc.getWorld().getName());
        guild.setHomeX(loc.getX());
        guild.setHomeY(loc.getY());
        guild.setHomeZ(loc.getZ());
        guild.setHomeYaw(loc.getYaw());
        guild.setHomePitch(loc.getPitch());
        UUID ownerUuid = p.getUniqueId();
        String ownerName = p.getName();
        if (!pendingGuildCreations.add(ownerUuid)) {
            p.sendMessage(ChatColor.GRAY + "Tworzenie gildii już trwa.");
            return;
        }
        p.sendMessage(ChatColor.GRAY + "Tworzę gildię...");
        guildManager.createGuildAsync(guild).whenComplete((createdGuild, error) -> {
            boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                    pendingGuildCreations.remove(ownerUuid);
                    Player owner = Bukkit.getPlayer(ownerUuid);
                    if (owner == null) {
                        if (error != null) {
                            plugin.getLogger().warning("Could not create guild " + tag + " for " + ownerName
                                    + ": " + error.getMessage());
                        } else if (createdGuild != null) {
                            userManager.updateGuildMembershipAsync(ownerUuid, createdGuild.getTag(), "OWNER")
                                    .exceptionally(profileError -> {
                                        plugin.getLogger().severe("Guild " + tag + " was created, but owner profile "
                                                + ownerUuid + " could not be updated: " + profileError.getMessage());
                                        return null;
                                    });
                        }
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Could not create guild " + tag + " for " + ownerName
                                + ": " + error.getMessage());
                        owner.sendMessage(ChatColor.RED + "Nie udało się utworzyć gildii. Spróbuj ponownie.");
                        return;
                    }
                    if (createdGuild == null) {
                        owner.sendMessage(ChatColor.RED + "Gildia o takim tagu już istnieje lub jest tworzona.");
                        return;
                    }
                    userManager.updateGuildMembershipAsync(ownerUuid, createdGuild.getTag(), "OWNER")
                            .whenComplete((ignored, profileError) -> BukkitTaskScheduler.execute(plugin, () -> {
                                Player currentOwner = Bukkit.getPlayer(ownerUuid);
                                if (currentOwner == null || !currentOwner.isOnline()) return;
                                if (profileError != null) {
                                    currentOwner.sendMessage(ChatColor.RED + "Gildia powstała, ale profil wymaga synchronizacji.");
                                    plugin.getLogger().severe("Could not update guild owner profile " + ownerUuid
                                            + ": " + profileError.getMessage());
                                }
                                finishGuildCreation(currentOwner, createdGuild);
                            }));
            });
            if (!scheduled) {
                pendingGuildCreations.remove(ownerUuid);
                if (createdGuild != null) {
                    userManager.updateGuildMembershipAsync(ownerUuid, createdGuild.getTag(), "OWNER")
                            .exceptionally(profileError -> {
                                plugin.getLogger().severe("Guild " + tag + " was created during shutdown, but owner "
                                        + ownerUuid + " profile could not be updated: " + profileError.getMessage());
                                return null;
                            });
                }
            }
        });
    }

    private void finishGuildCreation(Player owner, Guild guild) {
        Location center = new Location(
                Bukkit.getWorld(guild.getHomeWorld()),
                guild.getHomeX(),
                guild.getHomeY(),
                guild.getHomeZ()
        );
        if (center.getWorld() == null) {
            owner.sendMessage(ChatColor.RED + "Nie można utworzyć gildii: świat docelowy jest niedostępny.");
            return;
        }
        UUID ownerUuid = owner.getUniqueId();
        plugin.pasteSchematicAsync(center, "gildia_jajko").whenComplete((ignored, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentOwner = Bukkit.getPlayer(ownerUuid);
                    if (currentOwner != owner || !owner.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().warning("Could not paste guild schematic for " + guild.getTag() + ": "
                                + error.getMessage());
                        currentOwner.sendMessage(ChatColor.RED + "Nie udało się wkleić schem gildii.");
                    }

                    currentOwner.teleportAsync(center.clone().add(0.5, 1, 0.5))
                            .whenComplete((teleported, teleportError) -> BukkitTaskScheduler.execute(plugin, () -> {
                                if (Bukkit.getPlayer(ownerUuid) == owner && owner.isOnline()
                                        && (teleportError != null || !Boolean.TRUE.equals(teleported))) {
                                    owner.sendMessage(ChatColor.RED + "Gildia powstała, ale teleportacja nie powiodła się.");
                                }
                            }));
                    String message = "§6[GILDIA] §eGracz §f" + currentOwner.getName()
                            + " §estworzył§x gildie §f[" + guild.getTag() + "] §e" + guild.getName() + ".";
                    sendGlobal(message);
                    plugin.getScoreboardManager().updateBoard(currentOwner);
                    plugin.getGuildTagManager().refreshAllOnline();
                    currentOwner.sendMessage(ChatColor.GREEN + "Stworzyłeś gildię [" + guild.getTag() + "] "
                            + guild.getName() + ".");
                }));
    }



    // /gildia dolacz TAG
    private void handleJoin(Player p, User user, String[] args) {
        if (user.hasGuild()) {
            Guild existing = guildManager.getGuild(user.getGuildTag());
            if (existing == null) {
                user.setGuildTag(null);
                user.setGuildRole(null);
            } else {
                p.sendMessage(ChatColor.RED + "Już jesteś w gildii.");
                return;
            }
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia dolacz <tag>");
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Taka gildia nie istnieje.");
            return;
        }

        UUID uuid = p.getUniqueId();

        if (!guild.getInvitations().contains(uuid)) {
            p.sendMessage(ChatColor.RED + "Nie masz zaproszenia do tej gildii. Użyj /gildia akceptuj <tag> po otrzymaniu zaproszenia.");
            return;
        }

        guild.getInvitations().remove(uuid);
        guild.getMembers().add(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            plugin.getScoreboardManager().updateBoard(actor);
            actor.sendMessage(ChatColor.GREEN + "Dołączyłeś do gildii [" + guild.getTag() + "] "
                    + guild.getName() + ".");
            sendGlobal("§6[GILDIA] §eGracz §f" + actor.getName() + " §edołączył do gildii §f["
                    + guild.getTag() + "] §e" + guild.getName() + ".");
        });


    }

    // /gildia opusc
    private void handleLeave(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie jesteś w żadnej gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            // gildia zniknęła, tylko wyczyść usera
            user.setGuildTag(null);
            user.setGuildRole(null);
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje, dane wyczyszczono.");
            return;
        }

        UUID uuid = p.getUniqueId();

        if ("OWNER".equalsIgnoreCase(user.getGuildRole()) && guild.getMembers().size() > 1) {
            p.sendMessage(ChatColor.RED + "Najpierw przekaż lidera lub rozwiąż gildię (jeszcze nie zaimplementowane).");
            return;
        }

        guild.getMembers().remove(uuid);
        guild.getMods().remove(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "NONE"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            plugin.getScoreboardManager().updateBoard(actor);
            actor.sendMessage(ChatColor.YELLOW + "Opuściłeś gildię [" + tag + "] " + guild.getName() + ".");
            sendGlobal("§6[GILDIA] §eGracz §f" + actor.getName() + " §eopuścił gildię §f["
                    + tag + "] §e" + guild.getName() + ".");
        });

    }

    // /gildia info [tag]
    private void handleInfo(Player p, User user, String[] args) {
        String tag;
        if (args.length >= 2) {
            tag = GuildManager.normalizeTag(args[1]);
        } else if (user.hasGuild()) {
            tag = user.getGuildTag();
        } else {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia info <tag> lub będąc w gildii: /gildia info");
            return;
        }

        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Taka gildia nie istnieje.");
            return;
        }

        p.sendMessage(ChatColor.GOLD + "Gildia [" + guild.getTag() + "] " + guild.getName());
        p.sendMessage(ChatColor.GRAY + "Lider: " + formatName(guild.getOwner()));
        p.sendMessage(ChatColor.GRAY + "Członków: " + guild.getMembers().size());
        if (guild.getAlliedGuilds().isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Sojusze: " + ChatColor.DARK_GRAY + "brak");
        } else {
            String allies = String.join(", ", guild.getAlliedGuilds());
            p.sendMessage(ChatColor.GRAY + "Sojusze: " + ChatColor.YELLOW + allies);
        }

    }

    private String formatName(UUID uuid) {
        if (uuid == null) return "brak";
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString();
    }
}
