package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
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
            case "stworz":
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
            case "rozwiaz":
                handleRozwiaz(player, user);
                break;
            case "ustawdom":
                handleSetHomeGuild(player, user);
                break;
            case "dom":
                handleGuildHome(player, user);
                break;

            case "zapros":
            case "invite":
                handleInvite(player, user, args);
                break;
            case "akceptuj":
            case "accept":
                handleAccept(player, user, args);
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
        p.sendMessage(ChatColor.GRAY + "/gildia stworz <tag> <nazwa> " + ChatColor.DARK_GRAY + "- tworzy nową gildię");
        p.sendMessage(ChatColor.GRAY + "/gildia dolacz <tag> " + ChatColor.DARK_GRAY + "- dołącz do gildii");
        p.sendMessage(ChatColor.GRAY + "/gildia opusc " + ChatColor.DARK_GRAY + "- opuść swoją gildię");
        p.sendMessage(ChatColor.GRAY + "/gildia info [tag] " + ChatColor.DARK_GRAY + "- informacje o gildii");
        p.sendMessage(ChatColor.GRAY + "/gildia zapros <gracz> " + ChatColor.DARK_GRAY + "- zaproś gracza do gildii");
        p.sendMessage(ChatColor.GRAY + "/gildia akceptuj <tag> " + ChatColor.DARK_GRAY + "- zaakceptuj zaproszenie");
        p.sendMessage(ChatColor.GRAY + "/g mistrz <gracz> " + ChatColor.DARK_GRAY + "- nadaj/odbierz rangę mistrza (mod)");
        p.sendMessage(ChatColor.GRAY + "/g przekaz <gracz> " + ChatColor.DARK_GRAY + "- przekaż lidera gildii");
        p.sendMessage(ChatColor.GRAY + "/g rozwiaz " + ChatColor.DARK_GRAY + "- rozwiąż swoją gildię");
        p.sendMessage(ChatColor.GRAY + "/g sojusz <tag> " + ChatColor.DARK_GRAY + "- zawrzyj lub zerwij sojusz z gildią");
        p.sendMessage(ChatColor.GRAY + "/g wyrzuc <gracz> " + ChatColor.DARK_GRAY + "- wyrzuć gracza z gildii");
        p.sendMessage(ChatColor.GRAY + "/g ustawdom " + ChatColor.DARK_GRAY + "- ustaw dom gildii");
        p.sendMessage(ChatColor.GRAY + "/g dom " + ChatColor.DARK_GRAY + "- teleport do domu gildii");

    }
    // /g wyrzuc GRACZ
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
        Guild guild = guildManager.getGuild(tag);
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

        targetUser.setGuildTag(null);
        targetUser.setGuildRole(null);

        p.sendMessage(ChatColor.GREEN + "Wyrzuciłeś gracza " + target.getName() + " z gildii.");
        target.sendMessage(ChatColor.RED + "Zostałeś wyrzucony z gildii [" + tag + "].");

        // globalne info
        String msg = "§6[GILDIA] §eGracz §f" + target.getName() +
                " §ezostał wyrzucony z gildii §f[" + tag + "]§e.";
        sendGlobal(msg);
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
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        Location loc = p.getLocation();
        String sector = plugin.getSectorManager()
                .getSectorForLocation(loc.getBlockX(), loc.getBlockZ());

        guild.setHomeSector(sector);
        guild.setHomeWorld(loc.getWorld().getName());
        guild.setHomeX(loc.getX());
        guild.setHomeY(loc.getY());
        guild.setHomeZ(loc.getZ());
        guild.setHomeYaw(loc.getYaw());
        guild.setHomePitch(loc.getPitch());

        guildManager.saveGuild(guild);
        guildManager.reloadGuild(tag);


        p.sendMessage(ChatColor.GREEN + "Ustawiono dom gildii w tym miejscu. (§7Sektor: " +
                sector + "§a)");
    }
    // /g dom
    private void handleGuildHome(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.reloadGuild(tag); // zawsze świeża wersja
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

        new ai.aisector.task.GuildHomeTeleportWarmupTask(
                p,
                guild.getHomeSector(),
                json,
                plugin
        ).start();
    }


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
        Guild myGuild = guildManager.getGuild(myTag);
        if (myGuild == null) {
            p.sendMessage(ChatColor.RED + "Twoja gildia nie istnieje.");
            return;
        }

        String otherTag = args[1].toUpperCase();
        if (myTag.equalsIgnoreCase(otherTag)) {
            p.sendMessage(ChatColor.RED + "Nie możesz zrobić sojuszu z własną gildią.");
            return;
        }

        Guild otherGuild = guildManager.getGuild(otherTag);
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

            String msg = "§6[GILDIA] §eSojusz między gildiami §f[" + myTag + "] §ea §f[" + otherTag + "] §ezostał zerwany.";
            sendGlobal(msg);
            return;
        }

        // 2) jeśli mamy zaproszenie od tej gildii -> AKCEPTUJ
        if (myGuild.getAllyInvitations().contains(otherTag)) {
            myGuild.getAllyInvitations().remove(otherTag);
            myGuild.getAlliedGuilds().add(otherTag);
            otherGuild.getAlliedGuilds().add(myTag);

            guildManager.saveGuild(myGuild);
            guildManager.saveGuild(otherGuild);

            String msg = "§6[GILDIA] §eGildie §f[" + myTag + "] §eoraz §f[" + otherTag + "] §ezawarły sojusz.";
            sendGlobal(msg);
            return;
        }

        // 3) w przeciwnym wypadku -> WYSYŁAMY ZAPROSZENIE
        otherGuild.getAllyInvitations().add(myTag);
        guildManager.saveGuild(otherGuild);

        // opcjonalnie możesz zostawić sam title, bez lokalnych wiadomości
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

        // jeśli nie chcesz globala przy samym zaproszeniu, usuń te dwie linie:
        String msg = "§6[GILDIA] §f[" + myTag + "] §ewysłała propozycję sojuszu do gildii §f[" + otherTag + "]§e.";
        sendGlobal(msg);

        p.sendMessage(ChatColor.GREEN + "Wysłałeś propozycję sojuszu do gildii [" + otherTag + "].");
    }



    // /g rozwiaz
    private void handleRozwiaz(Player p, User user) {
        if (!user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Nie masz gildii.");
            return;
        }
        if (!"OWNER".equalsIgnoreCase(user.getGuildRole())) {
            p.sendMessage(ChatColor.RED + "Tylko lider może rozwiązać gildię.");
            return;
        }

        String tag = user.getGuildTag();
        Guild guild = guildManager.getGuild(tag);
        if (guild == null) {
            user.setGuildTag(null);
            user.setGuildRole(null);
            p.sendMessage(ChatColor.RED + "Gildia już nie istnieje, dane wyczyszczono.");
            String msg = "§6[GILDIA] §eGildia §f[" + tag + "] §e" + guild.getName() + " została rozwiązana.";
            sendGlobal(msg);

            return;
        }

        // wyczyść gildię wszystkim członkom online
        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                User memberUser = userManager.loadOrGetUser(member);
                if (memberUser != null) {
                    memberUser.setGuildTag(null);
                    memberUser.setGuildRole(null);
                    if (!memberId.equals(p.getUniqueId())) {
                        member.sendMessage(ChatColor.RED + "Twoja gildia [" + tag + "] została rozwiązana.");
                    }
                }
            }
        }

        guildManager.deleteGuild(guild);

        user.setGuildTag(null);
        user.setGuildRole(null);

        p.sendMessage(ChatColor.YELLOW + "Rozwiązałeś gildię [" + tag + "] " + guild.getName() + ".");
    }

    // /g przekaz GRACZ
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
        Guild guild = guildManager.getGuild(tag);
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

        // stary lider staje się zwykłym członkiem
        user.setGuildRole("MEMBER");

        // nowy lider
        targetUser.setGuildRole("OWNER");
        guild.setOwner(target.getUniqueId());
        guild.getMods().remove(target.getUniqueId()); // nie musi mieć jednocześnie MOD

        guildManager.saveGuild(guild);

        p.sendMessage(ChatColor.GREEN + "Przekazałeś przywództwo gildii graczowi " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "Zostałeś nowym liderem gildii [" + tag + "].");
    }

    // /g mistrz GRACZ  -> toggle MOD
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
        Guild guild = guildManager.getGuild(tag);
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
            // zdejmujemy moda
            guild.getMods().remove(uuid);
            guildManager.saveGuild(guild);

            if ("MOD".equalsIgnoreCase(targetUser.getGuildRole())) {
                targetUser.setGuildRole("MEMBER");
            }

            p.sendMessage(ChatColor.YELLOW + "Zabrałeś rangę mistrza graczowi " + target.getName() + ".");
            target.sendMessage(ChatColor.RED + "Straciłeś rangę mistrza w gildii [" + tag + "].");
        } else {
            // nadajemy moda
            guild.getMods().add(uuid);
            guildManager.saveGuild(guild);

            targetUser.setGuildRole("MOD");

            p.sendMessage(ChatColor.GREEN + "Nadałeś rangę mistrza graczowi " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "Otrzymałeś rangę mistrza w gildii [" + tag + "].");
        }
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
            p.sendMessage(ChatColor.RED + "Już jesteś w gildii.");
            return;
        }
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Użycie: /gildia stworz <tag> <nazwa>");
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

        user.setGuildTag(tag);
        user.setGuildRole("OWNER");
        String msg = "§6[GILDIA] §eGracz §f" + p.getName() +
                " §estworzył gildię §f[" + tag + "] §e" + name + ".";
        sendGlobal(msg);

        p.sendMessage(ChatColor.GREEN + "Stworzyłeś gildię [" + tag + "] " + name + ".");
    }

    // /gildia dolacz TAG
    private void handleJoin(Player p, User user, String[] args) {
        if (user.hasGuild()) {
            p.sendMessage(ChatColor.RED + "Najpierw opuść obecną gildię.");
            return;
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

        user.setGuildTag(tag);
        user.setGuildRole("MEMBER");

        p.sendMessage(ChatColor.GREEN + "Dołączyłeś do gildii [" + tag + "] " + guild.getName() + ".");
        String msg = "§6[GILDIA] §eGracz §f" + p.getName() +
                " §edołączył do gildii §f[" + tag + "] §e" + guild.getName() + ".";
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
