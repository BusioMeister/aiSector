package ai.aisector.scoreboard;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ScoreboardManager {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final UserManager userManager;

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String LOGO_CHAR = "\uE001";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ScoreboardManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.userManager = plugin.getUserManager();

        // Odświeżanie co sekundę – tylko update, bez niszczenia objective
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        updateBoard(player);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private Component gradient(String mm) {
        return miniMessage.deserialize(mm, TagResolver.resolver(StandardTags.color()));
    }

    public void createBoard(Player player) {
        org.bukkit.scoreboard.ScoreboardManager sbMan = Bukkit.getScoreboardManager();
        if (sbMan == null) return;

        Scoreboard board = sbMan.getNewScoreboard();
        Objective obj = board.registerNewObjective("aisector", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(LOGO_CHAR);

        registerTeamWithEntry(board, obj, "guild", "§7", 6);


        // tło
        obj.getScore(ChatColor.of("#202020") + "               ").setScore(13);

        // stałe entry (niewidoczne) dla dynamicznych linii
        registerTeamWithEntry(board, obj, "sector",  "§0", 12);
        obj.getScore(ChatColor.of("#000000") + "").setScore(11); // separator (inny string)

        registerTeamWithEntry(board, obj, "kills",   "§1", 9);
        registerTeamWithEntry(board, obj, "deaths",  "§2", 8);

        obj.getScore("  ").setScore(7); // separator

        registerTeamWithEntry(board, obj, "time",    "§3", 6);

        obj.getScore("   ").setScore(5); // separator

        registerTeamWithEntry(board, obj, "date",    "§4", 4);
        registerTeamWithEntry(board, obj, "clock",   "§5", 3);

        registerTeamWithEntry(board, obj, "ip",      "§6", 2);

        player.setScoreboard(board);
        updateBoard(player);
    }
    public void removeBoard(Player player) {
        if (player == null || !player.isOnline()) return;

        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard board = player.getScoreboard();

        // Jeśli gracz już ma główny scoreboard, nic nie rób
        if (board == null || board.equals(main)) return;

        Objective obj = board.getObjective("aisector");
        if (obj != null) {
            obj.unregister();
        }

        // Usuń wszystkie teamy aby nie zostawić ich w pamięci
        for (Team team : board.getTeams()) {
            team.unregister();
        }

        // Przywróć domyślny scoreboard
        player.setScoreboard(main);
    }

    private void registerTeamWithEntry(Scoreboard board, Objective obj, String teamName, String entry, int score) {
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        } else {
            team.getEntries().forEach(team::removeEntry);
        }
        team.addEntry(entry);
        obj.getScore(entry).setScore(score);
    }

    public void updateBoard(Player player) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard board = player.getScoreboard();

        if (board == null || board.equals(main)) {
            createBoard(player);
            return;
        }

        Objective obj = board.getObjective("aisector");
        if (obj == null) {
            createBoard(player);
            return;
        }

        User user = userManager.getUser(player.getUniqueId());
        if (user == null) {
            Team t = board.getTeam("loading");
            if (t == null) {
                t = board.registerNewTeam("loading");
                t.addEntry("§z");
                obj.getScore("§z").setScore(1);
            }
            t.prefix(Component.text(ChatColor.GRAY + "Wczytywanie..."));
            return;
        }

        int kills = user.getKills();
        int deaths = user.getDeaths();

        long totalSeconds = user.getPlayTimeSeconds();
        long sessionSeconds = (System.currentTimeMillis() - user.getSessionStartMillis()) / 1000L;
        if (sessionSeconds > 0) totalSeconds += sessionSeconds;
        String timePlayed = formatPlayTime(totalSeconds);

        String sector = sectorManager.getSectorForLocation(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ()
        );
        if (sector == null || sector.isEmpty()) sector = "Brak";

        LocalDateTime now = LocalDateTime.now(POLAND_ZONE);
        String dateStr = now.format(DATE_FORMAT);
        String timeStr = now.format(TIME_FORMAT);

        // SEKTOR (gradient)
        {
            Team t = board.getTeam("sector");
            if (t == null) registerTeamWithEntry(board, obj, "sector", "§0", 12);
            t = board.getTeam("sector");
            Component label = gradient("<gradient:#FF55FF:#FF99FF>⚑ Sektor:</gradient>");
            Component value = Component.text(" " + sector, NamedTextColor.WHITE);
            t.prefix(label.append(value));
        }
        {
            Team tg = board.getTeam("guild");
            if (tg == null) registerTeamWithEntry(board, obj, "guild", "§7", 6);
            tg = board.getTeam("guild");

            String guildTag = "-";
            if (user != null && user.hasGuild()) guildTag = user.getGuildTag();

            Component label = Component.text(ChatColor.YELLOW + "Gildia: ");
            Component value = Component.text(ChatColor.WHITE + guildTag);
            tg.prefix(label.append(value));

        }
        // ZABÓJSTWA (gradient)
        {
            Team t = board.getTeam("kills");
            if (t == null) registerTeamWithEntry(board, obj, "kills", "§1", 9);
            t = board.getTeam("kills");
            Component label = gradient("<gradient:#FB0000:#880000>⚔ Zabójstwa:</gradient>");
            Component value = Component.text(" " + kills, NamedTextColor.WHITE);
            t.prefix(label.append(value));
        }

        // ŚMIERCI (gradient)
        {
            Team t = board.getTeam("deaths");
            if (t == null) registerTeamWithEntry(board, obj, "deaths", "§2", 8);
            t = board.getTeam("deaths");
            Component label = gradient("<gradient:#FB0000:#880000>☠ Śmierci:</gradient>");
            Component value = Component.text(" " + deaths, NamedTextColor.WHITE);
            t.prefix(label.append(value));
        }

        // SPĘDZONY CZAS (gradient tak jak chciałeś)
        {
            Team t = board.getTeam("time");
            if (t == null) registerTeamWithEntry(board, obj, "time", "§3", 6);
            t = board.getTeam("time");
            Component label = gradient("<gradient:#00C8C8:#00FFFF>⏱ Spędzone:</gradient>");
            Component value = Component.text(" " + timePlayed, NamedTextColor.WHITE);
            t.prefix(label.append(value));
        }

        // DATA
        {
            Team t = board.getTeam("date");
            if (t == null) registerTeamWithEntry(board, obj, "date", "§4", 4);
            t = board.getTeam("date");
            String text = ChatColor.of("#dddddd") + "📅 " + ChatColor.WHITE + dateStr;
            t.prefix(Component.text(text));
        }

        // GODZINA
        {
            Team t = board.getTeam("clock");
            if (t == null) registerTeamWithEntry(board, obj, "clock", "§5", 3);
            t = board.getTeam("clock");
            String text = ChatColor.of("#dddddd") + "🕒 " + ChatColor.WHITE + timeStr;
            t.prefix(Component.text(text));
        }

        // IP (gradient na nazwie)
        {
            Team t = board.getTeam("ip");
            if (t == null) registerTeamWithEntry(board, obj, "ip", "§6", 2);
            t = board.getTeam("ip");
            Component label = gradient("<gradient:#FFCC00:#FF8800>LastPlay.NET</gradient>");
            Component prefix = Component.text(ChatColor.of("#aaaaaa") + "IP: ");
            t.prefix(prefix.append(label));
        }
    }

    private String formatPlayTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else {
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
