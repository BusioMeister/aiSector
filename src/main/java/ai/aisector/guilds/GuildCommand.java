package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
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

public class GuildCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final GuildManager guildManager;
    private final UserManager userManager;
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


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda tylko dla graczy.");
            return true;
        }

        Player player = (Player) sender;
        User user = userManager.loadOrGetUser(player);
        if (user == null) {
            player.sendMessage(ChatColor.RED + "Nie udało się załadować danych gracza.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
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


/// fsdfsdf


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
            guildManager.saveGuild(guild);
            p.sendMessage(ChatColor.YELLOW + "Powitanie gildii zostało wyłączone.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guild.setWelcomeMessage(message);
        guildManager.saveGuild(guild);
        String formatted = "§6[GILDIA] §f[" + tag + "] §e" + message;
        for (UUID memberId : guild.getMembers()) {
            Player m = Bukkit.getPlayer(memberId);
            if (m != null) m.sendMessage(formatted);
        }
        p.sendMessage(ChatColor.GREEN + "Ustawiono powitanie gildii.");
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
        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);

        p.sendMessage(ChatColor.YELLOW + "PvP wewnątrz gildii " +
                (newValue ? ChatColor.RED + "WŁĄCZONE" : ChatColor.GREEN + "WYŁĄCZONE") + ChatColor.YELLOW + ".");
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
        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);

        p.sendMessage(ChatColor.YELLOW + "PvP z sojuszami " +
                (newValue ? ChatColor.RED + "WŁĄCZONE" : ChatColor.GREEN + "WYŁĄCZONE") + ChatColor.YELLOW + ". "
                + ChatColor.GRAY + "Musi być tak samo ustawione w obu gildiach.");
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
            guildManager.saveGuild(guild);
            guildManager.reloadGuild(tag);
            p.sendMessage(ChatColor.YELLOW + "Zdjąłeś rangę zastępcy z " + target.getName() + ".");
            target.sendMessage(ChatColor.RED + "Straciłeś rangę zastępcy w gildii " + tag + ".");
        } else {
            guild.setDeputy(uuid);
            guildManager.saveGuild(guild);
            guildManager.reloadGuild(tag);
            p.sendMessage(ChatColor.GREEN + "Nadałeś rangę zastępcy graczowi " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "Otrzymałeś rangę zastępcy w gildii " + tag + ".");
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
            guildManager.saveGuild(guild);
            guildManager.reloadGuild(tag);
            if ("MOD".equalsIgnoreCase(targetUser.getGuildRole())) {
                targetUser.setGuildRole("MEMBER");
            }
            p.sendMessage(ChatColor.YELLOW + "Zabrałeś rangę mistrza graczowi " + target.getName() + ".");
            target.sendMessage(ChatColor.RED + "Straciłeś rangę mistrza w gildii " + tag + ".");
        } else {
            guild.getMods().add(uuid);
            guildManager.saveGuild(guild);
            guildManager.reloadGuild(tag);
            targetUser.setGuildRole("MOD");
            p.sendMessage(ChatColor.GREEN + "Nadałeś rangę mistrza graczowi " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "Otrzymałeś rangę mistrza w gildii " + tag + ".");
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
        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);

        targetUser.setGuildTag(null);
        targetUser.setGuildRole(null);

        p.sendMessage(ChatColor.GREEN + "Wyrzuciłeś gracza " + target.getName() + " z gildii.");
        target.sendMessage(ChatColor.RED + "Zostałeś wyrzucony z gildii " + tag + ".");

        String msg = "§6[GILDIA] §eGracz §f" + target.getName() + " §ezostał wyrzucony z gildii §f[" + tag + "]§e.";
        sendGlobal(msg);
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

        user.setGuildRole("MEMBER");
        targetUser.setGuildRole("OWNER");
        guild.setOwner(target.getUniqueId());
        guild.getMods().remove(target.getUniqueId());

        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);

        p.sendMessage(ChatColor.GREEN + "Przekazałeś przywództwo gildii graczowi " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "Zostałeś nowym liderem gildii " + tag + ".");
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

        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);

        p.sendMessage(ChatColor.GREEN + "Ustawiono dom gildii w tym miejscu.");
    }

    private void handleGuildHome(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // zawsze najnowsze dane
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }
        if (guild.getHomeSector() == null || guild.getHomeWorld() == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie ma ustawionego domu. Użyj /g ustawdom.");
            return;
        }

        World world = Bukkit.getWorld(guild.getHomeWorld());
        if (world == null) {
            p.sendMessage(ChatColor.RED + "Świat domu gildii nie istnieje.");
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

        String otherTag = args[1].toUpperCase();
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
        if (myGuild.getAlliedGuilds().contains(otherTag)) {
            myGuild.getAlliedGuilds().remove(otherTag);
            otherGuild.getAlliedGuilds().remove(myTag);

            guildManager.saveGuild(myGuild);
            guildManager.saveGuild(otherGuild);
            guildManager.reloadGuild(myTag);
            guildManager.reloadGuild(otherTag);

            String msg = "§6[GILDIA] §eSojusz między gildiami §f[" + myTag + "] §ea §f[" + otherTag + "] §ezostał zerwany.";
            sendGlobal(msg);
            return;
        }

        // 2) jeśli mamy zaproszenie od tej gildii -> AKCEPTUJ
        // (ktoś wcześniej zrobił /g sojusz <naszTag> i dodał myTag do allyInvitations)
        if (myGuild.getAllyInvitations().contains(otherTag)) {
            myGuild.getAllyInvitations().remove(otherTag);
            myGuild.getAlliedGuilds().add(otherTag);
            otherGuild.getAlliedGuilds().add(myTag);

            guildManager.saveGuild(myGuild);
            guildManager.saveGuild(otherGuild);
            guildManager.reloadGuild(myTag);
            guildManager.reloadGuild(otherTag);

            String msg = "§6[GILDIA] §eGildie §f[" + myTag + "] §eoraz §f[" + otherTag + "] §ezawarły sojusz.";
            sendGlobal(msg);
            return;
        }

        // 3) w przeciwnym wypadku -> WYSYŁAMY ZAPROSZENIE
        // zapisujemy zaproszenie po stronie drugiej gildii
        otherGuild.getAllyInvitations().add(myTag);
        guildManager.saveGuild(otherGuild);
        guildManager.reloadGuild(otherTag);

        // title dla członków docelowej gildii (bez lokalnych chatów)
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

        // jeśli chcesz globalną informację o samym zaproszeniu:
        String inviteMsg = "§6[GILDIA] §f[" + myTag + "] §ewysłała propozycję sojuszu do gildii §f[" + otherTag + "]§e.";
        sendGlobal(inviteMsg);

        p.sendMessage(ChatColor.GREEN + "Wysłałeś propozycję sojuszu do gildii [" + otherTag + "].");
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

        String msg = "§6[GILDIA] §eGildia §f[" + tag + "] §e" + guild.getName() + " została rozwiązana.";
        sendGlobal(msg);

        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                User memberUser = userManager.loadOrGetUser(member);
                if (memberUser != null) {
                    memberUser.setGuildTag(null);
                    memberUser.setGuildRole(null);
                }
                if (!memberId.equals(p.getUniqueId())) {
                    member.sendMessage(ChatColor.RED + "Twoja gildia [" + tag + "] została rozwiązana.");
                }
            }
        }

        guildManager.deleteGuild(guild);
        user.setGuildTag(null);
        user.setGuildRole(null);

        p.sendMessage(ChatColor.YELLOW + "Rozwiązałeś gildię [" + tag + "] " + guild.getName() + ".");
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

        guild.getInvitations().add(target.getUniqueId());
        guildManager.saveGuild(guild);

        p.sendMessage(ChatColor.GREEN + "Wysłałeś zaproszenie do gildii [" + tag + "] dla " + target.getName() + ".");
        target.sendMessage(ChatColor.YELLOW + "Otrzymałeś zaproszenie do gildii [" + tag + "] " + guild.getName()
                + ". Użyj /gildia akceptuj " + tag + " aby dołączyć.");
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

        String tag = args[1].toUpperCase();
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
        guildManager.saveGuild(guild);

        user.setGuildTag(tag);
        user.setGuildRole("MEMBER");

        p.sendMessage(ChatColor.GREEN + "Dołączyłeś do gildii [" + tag + "] " + guild.getName() + ".");
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

        String tag = args[1].toUpperCase();
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        if (tag.length() < 2 || tag.length() > 5) {
            p.sendMessage(ChatColor.RED + "Tag musi mieć od 2 do 5 znaków.");
            return;
        }
        if (guildManager.exists(tag)) {
            p.sendMessage(ChatColor.RED + "Gildia o takim tagu już istnieje.");
            return;
        }

        Guild guild = guildManager.createGuild(tag, name, p.getUniqueId());
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Nie udało się utworzyć gildii.");
            return;
        }

        if (user != null) {
            user.setGuildTag(guild.getTag());
            user.setGuildRole("OWNER");
        }

        String msg = "§6[GILDIA] §eGracz §f" + p.getName() +
                " §estworzył§x gildie §f[" + tag + "] §e" + name + ".";
        sendGlobal(msg);

        p.sendMessage(ChatColor.GREEN + "Stworzyłeś gildię [" + tag + "] " + name + ".");
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

        String tag = args[1].toUpperCase();
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
        guildManager.saveGuild(guild);

// USTAWIAMY DANE UŻYTKOWNIKA
        if (user != null) {
            user.setGuildTag(guild.getTag());
            user.setGuildRole("MEMBER");
        }

        p.sendMessage(ChatColor.GREEN + "Dołączyłeś do gildii [" + guild.getTag() + "] " + guild.getName() + ".");
        String msg = "§6[GILDIA] §eGracz §f" + p.getName() +
                " §edołączył do gildii §f[" + guild.getTag() + "] §e" + guild.getName() + ".";
        sendGlobal(msg);


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
        guildManager.saveGuild(guild);

        user.setGuildTag(null);
        user.setGuildRole(null);

        p.sendMessage(ChatColor.YELLOW + "Opuściłeś gildię [" + tag + "] " + guild.getName() + ".");
        String msg = "§6[GILDIA] §eGracz §f" + p.getName() +
                " §eopuścił gildię §f[" + tag + "] §e" + guild.getName() + ".";
        sendGlobal(msg);

    }

    // /gildia info [tag]
    private void handleInfo(Player p, User user, String[] args) {
        String tag;
        if (args.length >= 2) {
            tag = args[1].toUpperCase();
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
