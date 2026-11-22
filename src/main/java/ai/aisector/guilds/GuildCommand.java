package ai.aisector.guilds;

import ai.aisector.utils.Messages;

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
    private final GuildPanel panel;
    private final Set<UUID> pendingGuildSyncs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingGuildCreations = ConcurrentHashMap.newKeySet();
    private static final String GLOBAL_CHANNEL = "global:chat";

    public GuildCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
        this.panel = new GuildPanel(plugin);
        Bukkit.getPluginManager().registerEvents(panel, plugin);
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
                            currentActor.sendMessage(Messages.text("guild-command.zmiany-gildii-nie-zostaly-zapisane-cache-zostal", ChatColor.RED, tag));
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
                                        currentActor.sendMessage(Messages.text("guild-command.gildia-zostala-zapisana-ale-profili-czlonkow-nie", ChatColor.RED));
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
            sender.sendMessage(Messages.text("guild-command.ta-komenda-tylko-dla-graczy"));
            return true;
        }

        Player player = (Player) sender;
        if (!guildManager.isReady()) {
            player.sendMessage(Messages.text("guild-command.dane-gildii-sa-jeszcze-ladowane-sprobuj-ponownie", ChatColor.GRAY));
            return true;
        }
        User user = userManager.loadOrGetUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("guild-command.nie-udalo-sie-zaladowac-danych-gracza", ChatColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        if (user.hasGuild() && guildManager.getGuildCached(user.getGuildTag()) == null) {
            UUID playerUuid = player.getUniqueId();
            if (!pendingGuildSyncs.add(playerUuid)) {
                player.sendMessage(Messages.text("guild-command.synchronizuje-dane-gildii-sprobuj-ponownie-za-chwile", ChatColor.GRAY));
                return true;
            }

            String guildTag = user.getGuildTag();
            player.sendMessage(Messages.text("guild-command.synchronizuje-dane-gildii", ChatColor.GRAY));
            guildManager.reloadGuildAsync(guildTag).whenComplete((guild, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        pendingGuildSyncs.remove(playerUuid);
                        Player currentPlayer = Bukkit.getPlayer(playerUuid);
                        if (currentPlayer != player || !player.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(Messages.text("guild-command.nie-udalo-sie-zsynchronizowac-gildii-sprobuj-ponownie", ChatColor.RED));
                            plugin.getLogger().warning("Could not synchronize guild " + guildTag + " for "
                                    + playerUuid + ": " + error.getMessage());
                            return;
                        }

                        User currentUser = userManager.getUser(playerUuid);
                        if (currentUser == null) {
                            currentPlayer.sendMessage(Messages.text("guild-command.dane-gracza-sa-nadal-ladowane", ChatColor.RED));
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
            if (guildManager.isPersistencePending(user.getGuildTag()) || guildManager.permissionsRefreshing(user.getGuildTag())) {
                player.sendMessage(Messages.text("guild-command.zmiany-gildii-sa-jeszcze-zapisywane-sprobuj-za", ChatColor.GRAY));
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
                player.sendMessage(Messages.text("guild-command.synchronizuje-dane-gildii-sprobuj-ponownie-za-chwile", ChatColor.GRAY));
                return true;
            }
            player.sendMessage(Messages.text("guild-command.wczytuje-gildie-z-mongo", ChatColor.GRAY, requestedGuildTag));
            guildManager.reloadGuildAsync(requestedGuildTag).whenComplete((loadedGuild, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        pendingGuildSyncs.remove(playerUuid);
                        Player currentPlayer = Bukkit.getPlayer(playerUuid);
                        if (currentPlayer != player || !player.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(Messages.text("guild-command.nie-udalo-sie-wczytac-gildii-sprobuj-ponownie", ChatColor.RED));
                            plugin.getLogger().warning("Could not load requested guild " + requestedGuildTag + ": "
                                    + error.getMessage());
                        } else if (loadedGuild == null) {
                            currentPlayer.sendMessage(Messages.text("guild-command.gildia-o-tagu-nie-istnieje", ChatColor.RED, requestedGuildTag));
                        } else {
                            onCommand(currentPlayer, cmd, label, args.clone());
                        }
                    });
                if (!scheduled) pendingGuildSyncs.remove(playerUuid);
            });
            return true;
        }

        switch (sub) {
            case "panel":
                panel.open(player, user);
                break;
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
        p.sendMessage(Messages.text("guild-command.gildia-komendy", ChatColor.YELLOW));
        p.sendMessage(Messages.text("guild-command.g-zaloz-tworzy-nowa-gildie", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-dolacz-dolacz-do-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-opusc-opusc-swoja-gildie", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-info-tag-informacje-o-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-zapros-zapros-gracza-do-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-akceptuj-zaakceptuj-zaproszenie", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-mistrz-nadaj-odbierz-range-mistrza-mod", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-przekaz-przekaz-lidera-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-usun-rozwiaz-swoja-gildie", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-sojusz-zawrzyj-lub-zerwij-sojusz-z", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-wyrzuc-wyrzuc-gracza-z-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-ustawdom-ustaw-dom-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-dom-teleport-do-domu-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-zastepca-nadaj-odbierz-range-zastepcy", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-pvp-wlacz-wylacz-pvp-w-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-pvpsojusz-wlacz-wylacz-pvp-z-sojuszami", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-alert-wysyla-jednorazowy-alert-na-ekran", ChatColor.GRAY, ChatColor.DARK_GRAY));
        p.sendMessage(Messages.text("guild-command.g-alertchat-ustawia-powitanie-dla-gildii", ChatColor.GRAY, ChatColor.DARK_GRAY));


    }
    // /g alertchat TREŚĆ | off
    private void handleGuildWelcome(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.ANNOUNCE)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-mistrz-moze-zmieniac-powitanie", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-alertchat", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("off")) {
            guild.setWelcomeMessage(null);
            persistGuildsThen(p, actor ->
                    actor.sendMessage(Messages.text("guild-command.powitanie-gildii-zostalo-wylaczone", ChatColor.YELLOW)), guild);
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.length() > 256) {
            p.sendMessage(Messages.text("guild-command.powitanie-moze-miec-maksymalnie-256-znakow", ChatColor.RED));
            return;
        }
        guild.setWelcomeMessage(message);
        persistGuildsThen(p, actor -> {
            String formatted = "§6[GILDIA] §f[" + tag + "] §e" + message;
            for (UUID memberId : guild.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) member.sendMessage(formatted);
            }
            actor.sendMessage(Messages.text("guild-command.ustawiono-powitanie-gildii", ChatColor.GREEN));
        }, guild);
    }

    // /g alert TREŚĆ
    private void handleGuildAlert(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.ANNOUNCE)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-mistrz-moze-wysylac-alerty", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-alert", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.length() > 256) {
            p.sendMessage(Messages.text("guild-command.alert-moze-miec-maksymalnie-256-znakow", ChatColor.RED));
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

        p.sendMessage(Messages.text("guild-command.wyslano-alert-do-gildii", ChatColor.GREEN));
    }

    // /g pvp
    private void handlePvpToggle(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.PVP)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-mistrz-moze-zmieniac-ustawienia", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        boolean newValue = !guild.isFriendlyFireGuild();
        guild.setFriendlyFireGuild(newValue);
        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(tag);
            actor.sendMessage(Messages.text("guild-command.pvp-protection", newValue ? "§cWYŁĄCZONA" : "§aWŁĄCZONA"));
        }, guild);
    }

    private void handlePvpAlliesToggle(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.PVP)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-mistrz-moze-zmieniac-ustawienia", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        boolean newValue = !guild.isFriendlyFireAllies();
        guild.setFriendlyFireAllies(newValue);
        persistGuildsThen(p, actor -> {
            guildManager.reloadGuild(tag);
            actor.sendMessage(Messages.text("guild-command.pvp-z-sojuszami-musi-byc-tak-samo", ChatColor.YELLOW, (newValue ? ChatColor.RED + "WŁĄCZONE" : ChatColor.GREEN + "WYŁĄCZONE"), ChatColor.YELLOW, ChatColor.GRAY));
        }, guild);
    }


    // /g zastepca GRACZ  -> toggle deputy
    private void handleDeputy(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-moze-ustawic-zastepce", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-zastepca", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-online", ChatColor.RED));
            return;
        }
        if (!guild.getMembers().contains(target.getUniqueId())) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-w-twojej-gildii", ChatColor.RED));
            return;
        }
        if (guild.getOwner() != null && guild.getOwner().equals(target.getUniqueId())) {
            p.sendMessage(Messages.text("guild-command.lider-nie-moze-byc-zastepca", ChatColor.RED));
            return;
        }

        UUID uuid = target.getUniqueId();
        if (guild.getDeputy() != null && guild.getDeputy().equals(uuid)) {
            guild.assignRank(uuid, GuildRank.RECRUIT);
            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(Messages.text("guild-command.zdjales-range-zastepcy-z", ChatColor.YELLOW, targetName));
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(Messages.text("guild-command.straciles-range-zastepcy-w-gildii", ChatColor.RED, tag));
                }
            }, guild);
        } else {
            guild.assignRank(uuid, GuildRank.DEPUTY);
            persistGuildsThen(p, actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(Messages.text("guild-command.nadales-range-zastepcy-graczowi", ChatColor.GREEN, targetName));
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(Messages.text("guild-command.otrzymales-range-zastepcy-w-gildii", ChatColor.GREEN, tag));
                }
            }, guild);
        }
    }

    private void handleMistrz(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-gildii-moze-nadawac-range-mistrza", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-mistrz", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-online", ChatColor.RED));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Messages.text("guild-command.nie-mozesz-nadac-sobie-rangi-mistrza", ChatColor.RED));
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-w-twojej-gildii", ChatColor.RED));
            return;
        }

        UUID uuid = target.getUniqueId();
        if (guild.getMods().contains(uuid)) {
            guild.assignRank(uuid, GuildRank.RECRUIT);
            persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(Messages.text("guild-command.zabrales-range-mistrza-graczowi", ChatColor.YELLOW, targetName));
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(Messages.text("guild-command.straciles-range-mistrza-w-gildii", ChatColor.RED, tag));
                }
            });
        } else {
            guild.assignRank(uuid, GuildRank.MASTER);
            persistGuildMemberships(p, guild, Map.of(uuid, "MOD"), actor -> {
                guildManager.reloadGuild(tag);
                plugin.getGuildTagManager().refreshAllOnline();
                actor.sendMessage(Messages.text("guild-command.nadales-range-mistrza-graczowi", ChatColor.GREEN, targetName));
                Player currentTarget = Bukkit.getPlayer(uuid);
                if (currentTarget != null) {
                    currentTarget.sendMessage(Messages.text("guild-command.otrzymales-range-mistrza-w-gildii", ChatColor.GREEN, tag));
                }
            });
        }
    }

    private void handleKick(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.KICK)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-mistrz-moze-wyrzucac-graczy", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-wyrzuc", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-online", ChatColor.RED));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Messages.text("guild-command.nie-mozesz-wyrzucic-samego-siebie-uzyj-g", ChatColor.RED));
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-w-twojej-gildii", ChatColor.RED));
            return;
        }
        UUID uuid = target.getUniqueId();
        if (uuid.equals(guild.getOwner())) {
            p.sendMessage(Messages.text("guild-command.nie-mozesz-wyrzucic-lidera-gildii", ChatColor.RED));
            return;
        }
        if (uuid.equals(guild.getDeputy()) && !p.getUniqueId().equals(guild.getOwner())) {
            p.sendMessage(Messages.text("guild-panel.only-leader"));
            return;
        }

        guild.getMembers().remove(uuid);
        guild.clearRank(uuid);
        String kickedName = target.getName();
        persistGuildMemberships(p, guild, Map.of(uuid, "NONE"), actor -> {
            guildManager.reloadGuild(tag);
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(Messages.text("guild-command.wyrzuciles-gracza-z-gildii", ChatColor.GREEN, kickedName));
            Player currentTarget = Bukkit.getPlayer(uuid);
            if (currentTarget != null) {
                currentTarget.sendMessage(Messages.text("guild-command.zostales-wyrzucony-z-gildii", ChatColor.RED, tag));
            }
            sendGlobal(Messages.text("guild-command.gildia-gracz-zostal-wyrzucony-z-gildii", kickedName, tag));
        });
    }

    private void handlePrzekaz(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-moze-przekazac-gildie", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-przekaz", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-online", ChatColor.RED));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Messages.text("guild-command.juz-jestes-liderem", ChatColor.RED));
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null || !tag.equalsIgnoreCase(targetUser.getGuildTag())) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-w-twojej-gildii", ChatColor.RED));
            return;
        }

        guild.setOwner(target.getUniqueId());
        guild.clearRank(target.getUniqueId());

        UUID currentOwnerUuid = p.getUniqueId();
        UUID newOwnerUuid = target.getUniqueId();
        persistGuildMemberships(p, guild, Map.of(currentOwnerUuid, "MEMBER", newOwnerUuid, "OWNER"), actor -> {
            guildManager.reloadGuild(tag);
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(Messages.text("guild-command.przekazales-przywodztwo-gildii-graczowi", ChatColor.GREEN, targetName));
            Player newOwner = Bukkit.getPlayer(newOwnerUuid);
            if (newOwner != null) newOwner.sendMessage(Messages.text("guild-command.zostales-nowym-liderem-gildii", ChatColor.GREEN, tag));
        });
    }

    // /g ustawdom
    private void handleSetHomeGuild(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.SET_HOME)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-moze-ustawic-dom-gildii", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // świeża gildia
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        Location loc = p.getLocation();
        String sector = plugin.getSectorManager().getSectorForLocation(loc.getBlockX(), loc.getBlockZ());
        if (guild.territory() == null || !guild.territory().contains(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ())) {
            p.sendMessage(Messages.text("guild-panel.home-in-territory"));
            return;
        }

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
            actor.sendMessage(Messages.text("guild-command.ustawiono-dom-gildii-w-tym-miejscu", ChatColor.GREEN));
        }, guild);
    }

    private void handleGuildHome(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // zawsze najnowsze dane
        plugin.getGuildTagManager().refreshAllOnline();

        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }
        if (guild.getHomeSector() == null || guild.getHomeWorld() == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-ma-ustawionego-domu-uzyj", ChatColor.RED));
            return;
        }

        try {
            StoredHomeData.validateLocation(guild.getHomeWorld(), guild.getHomeX(), guild.getHomeY(),
                    guild.getHomeZ(), guild.getHomeYaw(), guild.getHomePitch());
        } catch (IllegalArgumentException invalidHome) {
            p.sendMessage(Messages.text("guild-command.zapis-domu-gildii-jest-nieprawidlowy", ChatColor.RED));
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("world", guild.getHomeWorld());
        json.addProperty("x", guild.getHomeX());
        json.addProperty("y", guild.getHomeY());
        json.addProperty("z", guild.getHomeZ());
        json.addProperty("yaw", guild.getHomeYaw());
        json.addProperty("pitch", guild.getHomePitch());

        p.sendMessage(Messages.text("guild-command.rozpoczynam-teleportacje-do-domu-gildii-nie-ruszaj", ChatColor.YELLOW));
        new ai.aisector.task.GuildHomeTeleportWarmupTask(p, guild.getHomeSector(), json, plugin).start();
    }
    // /g sojusz <tag>
// 1) jeśli jest już sojusz -> zerwij
// 2) jeśli mamy zaproszenie od tej gildii -> akceptuj (zawrzyj sojusz)
// 3) w przeciwnym wypadku -> wyślij zaproszenie (title + ewentualny global)
    private void handleAlly(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.ALLIANCE)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-gildii-moze-zarzadzac-sojuszami", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-g-sojusz", ChatColor.RED));
            return;
        }

        String myTag = user.getGuildTag();
        // zawsze świeża wersja z Mongo (ważne między sektorami)
        Guild myGuild = guildManager.reloadGuild(myTag);
        if (myGuild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        String otherTag = GuildManager.normalizeTag(args[1]);
        if (myTag.equalsIgnoreCase(otherTag)) {
            p.sendMessage(Messages.text("guild-command.nie-mozesz-zrobic-sojuszu-z-wlasna-gildia", ChatColor.RED));
            return;
        }

        // też ładujemy z bazy, żeby widzieć gildię z innego sektora
        Guild otherGuild = guildManager.reloadGuild(otherTag);
        if (otherGuild == null) {
            p.sendMessage(Messages.text("guild-command.gildia-o-tagu-nie-istnieje", ChatColor.RED, otherTag));
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
                sendGlobal(Messages.text("guild-command.gildia-sojusz-miedzy-gildiami-a-zostal-zerwany", myTag, otherTag));
                actor.sendMessage(Messages.text("guild-command.zerwano-sojusz-z-gildia", ChatColor.GREEN, otherTag));
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
                sendGlobal(Messages.text("guild-command.gildia-gildie-oraz-zawarly-sojusz", myTag, otherTag));
                actor.sendMessage(Messages.text("guild-command.zawarto-sojusz-z-gildia", ChatColor.GREEN, otherTag));
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
                            Messages.text("guild-command.sojusz-od", ChatColor.GOLD, myTag),
                            Messages.text("guild-command.lider-g-sojusz", ChatColor.YELLOW, myTag),
                            10, 60, 10
                    );
                }
            }
            sendGlobal(Messages.text("guild-command.gildia-wyslala-propozycje-sojuszu-do-gildii", myTag, otherTag));
            actor.sendMessage(Messages.text("guild-command.wyslales-propozycje-sojuszu-do-gildii", ChatColor.GREEN, otherTag));
        }, otherGuild);
    }




    // /g rozwiaz
    private void handleUsun(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-moze-rozwiazac-gildie", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag);
        if (guild == null) {
            user.setGuildTag(null);
            user.setGuildRole(null);
            p.sendMessage(Messages.text("guild-command.gildia-juz-nie-istnieje-dane-wyczyszczono", ChatColor.RED));
            return;
        }
        UUID ownerUuid = p.getUniqueId();
        String guildName = guild.getName();
        List<UUID> members = new ArrayList<>(guild.getMembers());
        p.sendMessage(Messages.text("guild-command.rozwiazuje-gildie", ChatColor.GRAY));
        guildManager.deleteGuildAsync(guild).whenComplete((deleted, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    Player owner = Bukkit.getPlayer(ownerUuid);
                    if (error != null || !Boolean.TRUE.equals(deleted)) {
                        if (owner != null) {
                            owner.sendMessage(Messages.text("guild-command.nie-udalo-sie-bezpiecznie-rozwiazac-gildii-sprobuj", ChatColor.RED));
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
                            member.sendMessage(Messages.text("guild-command.twoja-gildia-zostala-rozwiazana", ChatColor.RED, tag));
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
                        owner.sendMessage(Messages.text("guild-command.rozwiazales-gildie", ChatColor.YELLOW, tag, guildName));
                    }
                    sendGlobal(Messages.text("guild-command.gildia-gildia-zostala-rozwiazana", tag, guildName));
                }));
    }

    private boolean hasRight(Player player, User user, GuildRank.Right right) {
        Guild guild = guildManager.getGuild(user.getGuildTag());
        return guild != null && guild.allows(player.getUniqueId(), right);
    }

    // /gildia zapros GRACZ
    private void handleInvite(Player p, User user, String[] args) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-masz-gildii", ChatColor.RED));
            return;
        }
        if (!hasRight(p, user, GuildRank.Right.INVITE)) {
            p.sendMessage(Messages.text("guild-command.tylko-lider-lub-moderator-moze-zapraszac", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-gildia-zapros", ChatColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-nie-jest-online", ChatColor.RED));
            return;
        }

        User targetUser = userManager.loadOrGetUser(target);
        if (targetUser == null) {
            p.sendMessage(Messages.text("guild-command.nie-udalo-sie-zaladowac-danych-tego-gracza", ChatColor.RED));
            return;
        }
        if (targetUser.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.ten-gracz-juz-jest-w-gildii", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        UUID targetUuid = target.getUniqueId();
        guild.getInvitations().add(targetUuid);
        persistGuildsThen(p, actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(Messages.text("guild-command.wyslales-zaproszenie-do-gildii-dla", ChatColor.GREEN, tag, targetName));
            Player currentTarget = Bukkit.getPlayer(targetUuid);
            if (currentTarget != null) {
                currentTarget.sendMessage(Messages.text("guild-command.otrzymales-zaproszenie-do-gildii-uzyj-gildia-akceptuj", ChatColor.YELLOW, tag, guild.getName(), tag));
            }
        }, guild);
    }
    // /gildia akceptuj TAG
    private void handleAccept(Player p, User user, String[] args) {
        if (user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.najpierw-opusc-obecna-gildie", ChatColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-gildia-akceptuj", ChatColor.RED));
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.taka-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        UUID uuid = p.getUniqueId();
        if (!guild.getInvitations().contains(uuid)) {
            p.sendMessage(Messages.text("guild-command.nie-masz-zaproszenia-do-tej-gildii", ChatColor.RED));
            return;
        }

        guild.getInvitations().remove(uuid);
        guild.getMembers().add(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            actor.sendMessage(Messages.text("guild-command.dolaczyles-do-gildii", ChatColor.GREEN, tag, guild.getName()));
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
            p.sendMessage(Messages.text("guild-command.uzycie-gildia-zaloz", ChatColor.RED));
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        if (tag == null || !tag.matches("[A-Z0-9]{2,5}")) {
            p.sendMessage(Messages.text("guild-command.tag-musi-miec-od-2-do-5", ChatColor.RED));
            return;
        }
        if (name.isBlank() || name.length() > 32) {
            p.sendMessage(Messages.text("guild-command.nazwa-gildii-musi-miec-od-1-do", ChatColor.RED));
            return;
        }
        Location loc = p.getLocation();
        String sector = plugin.getSectorManager().getSectorForLocation(loc.getBlockX(), loc.getBlockZ());
        var localSector = sector == null ? null : plugin.getSectorManager().getSectorByName(sector);
        int radius = GuildTerritory.RADIUS;
        if (plugin.getSectorManager().isLockedSpawnBackend() || localSector == null
                || !plugin.getSectorManager().owns(loc)
                || !localSector.isInside(loc.getBlockX() - radius, loc.getBlockZ() - radius)
                || !localSector.isInside(loc.getBlockX() + radius, loc.getBlockZ() + radius)) {
            p.sendMessage(Messages.text("guild-panel.territory-outside"));
            return;
        }
        Guild guild = new Guild(tag, name, p.getUniqueId());
        guild.territory(new GuildTerritory(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()));
        guild.setHomeSector(sector);
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
            p.sendMessage(Messages.text("guild-command.tworzenie-gildii-juz-trwa", ChatColor.GRAY));
            return;
        }
        p.sendMessage(Messages.text("guild-command.tworze-gildie", ChatColor.GRAY));
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
                        Throwable cause = error;
                        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                        if (cause instanceof GuildManager.TerritoryOccupied) {
                            owner.sendMessage(Messages.text("guild-panel.territory-overlap"));
                            return;
                        }
                        plugin.getLogger().warning("Could not create guild " + tag + " for " + ownerName
                                + ": " + error.getMessage());
                        owner.sendMessage(Messages.text("guild-command.nie-udalo-sie-utworzyc-gildii-sprobuj-ponownie", ChatColor.RED));
                        return;
                    }
                    if (createdGuild == null) {
                        owner.sendMessage(Messages.text("guild-command.gildia-o-takim-tagu-juz-istnieje-lub", ChatColor.RED));
                        return;
                    }
                    userManager.updateGuildMembershipAsync(ownerUuid, createdGuild.getTag(), "OWNER")
                            .whenComplete((ignored, profileError) -> BukkitTaskScheduler.execute(plugin, () -> {
                                Player currentOwner = Bukkit.getPlayer(ownerUuid);
                                if (currentOwner == null || !currentOwner.isOnline()) return;
                                 if (profileError != null) {
                                     currentOwner.sendMessage(Messages.text("guild-command.gildia-powstala-ale-profil-wymaga-synchronizacji", ChatColor.RED));
                                     plugin.getLogger().severe("Could not update guild owner profile " + ownerUuid
                                             + ": " + profileError.getMessage());
                                     return;
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
        owner.sendMessage(Messages.text("guild-command.stworzyles-gildie", ChatColor.GREEN, guild.getTag(), guild.getName()));
        sendGlobal(Messages.text("guild-command.gildia-gracz-stworzyl-gildie", owner.getName(), guild.getTag(), guild.getName()));
        plugin.getScoreboardManager().updateBoard(owner);
        plugin.getGuildTagManager().refreshAllOnline();
        if (!plugin.getConfig().getBoolean("guild.placement.paste-schematic", false)) return;

        Location center = new Location(
                Bukkit.getWorld(guild.getHomeWorld()),
                guild.getHomeX(),
                guild.getHomeY(),
                guild.getHomeZ()
        );
        if (center.getWorld() == null) {
            owner.sendMessage(Messages.text("guild-command.gildia-powstala-ale-jej-swiat-jest-niedostepny", ChatColor.YELLOW));
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
                        currentOwner.sendMessage(Messages.text("guild-command.gildia-powstala-ale-nie-wklejono-jej-schematu", ChatColor.YELLOW));
                        return;
                    }

                    currentOwner.teleportAsync(center.clone().add(0.5, 1, 0.5))
                            .whenComplete((teleported, teleportError) -> BukkitTaskScheduler.execute(plugin, () -> {
                                if (Bukkit.getPlayer(ownerUuid) == owner && owner.isOnline()
                                        && (teleportError != null || !Boolean.TRUE.equals(teleported))) {
                                    owner.sendMessage(Messages.text("guild-command.gildia-powstala-ale-teleportacja-nie-powiodla-sie", ChatColor.RED));
                                }
                            }));
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
                p.sendMessage(Messages.text("guild-command.juz-jestes-w-gildii", ChatColor.RED));
                return;
            }
        }
        if (args.length < 2) {
            p.sendMessage(Messages.text("guild-command.uzycie-gildia-dolacz", ChatColor.RED));
            return;
        }

        String tag = GuildManager.normalizeTag(args[1]);
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.taka-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        UUID uuid = p.getUniqueId();

        if (!guild.getInvitations().contains(uuid)) {
            p.sendMessage(Messages.text("guild-command.nie-masz-zaproszenia-do-tej-gildii-uzyj", ChatColor.RED));
            return;
        }

        guild.getInvitations().remove(uuid);
        guild.getMembers().add(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "MEMBER"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            plugin.getScoreboardManager().updateBoard(actor);
            actor.sendMessage(Messages.text("guild-command.dolaczyles-do-gildii", ChatColor.GREEN, guild.getTag(), guild.getName()));
            sendGlobal(Messages.text("guild-command.gildia-gracz-dolaczyl-do-gildii", actor.getName(), guild.getTag(), guild.getName()));
        });


    }

    // /gildia opusc
    private void handleLeave(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(Messages.text("guild-command.nie-jestes-w-zadnej-gildii", ChatColor.RED));
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            // gildia zniknęła, tylko wyczyść usera
            user.setGuildTag(null);
            user.setGuildRole(null);
            p.sendMessage(Messages.text("guild-command.twoja-gildia-nie-istnieje-dane-wyczyszczono", ChatColor.RED));
            return;
        }

        UUID uuid = p.getUniqueId();

        if ("OWNER".equalsIgnoreCase(user.getGuildRole()) && guild.getMembers().size() > 1) {
            p.sendMessage(Messages.text("guild-command.najpierw-przekaz-lidera-lub-rozwiaz-gildie-jeszcze", ChatColor.RED));
            return;
        }

        guild.getMembers().remove(uuid);
        guild.clearRank(uuid);
        persistGuildMemberships(p, guild, Map.of(uuid, "NONE"), actor -> {
            plugin.getGuildTagManager().refreshAllOnline();
            plugin.getScoreboardManager().updateBoard(actor);
            actor.sendMessage(Messages.text("guild-command.opusciles-gildie", ChatColor.YELLOW, tag, guild.getName()));
            sendGlobal(Messages.text("guild-command.gildia-gracz-opuscil-gildie", actor.getName(), tag, guild.getName()));
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
            p.sendMessage(Messages.text("guild-command.uzycie-gildia-info-lub-bedac-w-gildii", ChatColor.RED));
            return;
        }

        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(Messages.text("guild-command.taka-gildia-nie-istnieje", ChatColor.RED));
            return;
        }

        p.sendMessage(Messages.text("guild-command.gildia", ChatColor.GOLD, guild.getTag(), guild.getName()));
        p.sendMessage(Messages.text("guild-command.lider", ChatColor.GRAY, formatName(guild.getOwner())));
        p.sendMessage(Messages.text("guild-command.czlonkow", ChatColor.GRAY, guild.getMembers().size()));
        if (guild.getAlliedGuilds().isEmpty()) {
            p.sendMessage(Messages.text("guild-command.sojusze-brak", ChatColor.GRAY, ChatColor.DARK_GRAY));
        } else {
            String allies = String.join(", ", guild.getAlliedGuilds());
            p.sendMessage(Messages.text("guild-command.sojusze", ChatColor.GRAY, ChatColor.YELLOW, allies));
        }

    }

    private String formatName(UUID uuid) {
        if (uuid == null) return "brak";
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString();
    }
}
