package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class GuildTagManager {

    private final SectorPlugin plugin;
    private final UserManager userManager;
    private final Scoreboard scoreboard;
    private final Team greenTeam;
    private final Team redTeam;

    public GuildTagManager(SectorPlugin plugin) {
        Bukkit.getLogger().info("[GuildTagManager] init");

        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        this.greenTeam = getOrCreateTeam("guild_green");
        this.greenTeam.setColor(org.bukkit.ChatColor.GREEN);

        this.redTeam = getOrCreateTeam("guild_red");
        this.redTeam.setColor(org.bukkit.ChatColor.RED);
    }

    private Team getOrCreateTeam(String name) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        return team;
    }

    // Główna metoda – wołamy ją dla WIDZA
    public void updateTagsFor(Player viewer) {
        Bukkit.getLogger().info("[GuildTagManager] updateTagsFor " + viewer.getName());
        viewer.sendMessage("§e[DEBUG] updateTagsFor");

        User viewerUser = userManager.loadOrGetUser(viewer);
        viewer.sendMessage("§e[DEBUG] viewer tag=" + (viewerUser != null ? viewerUser.getGuildTag() : "null"));
        scoreboard.getEntries().forEach(entry -> {
            greenTeam.removeEntry(entry);
            redTeam.removeEntry(entry);
        });

        for (Player target : Bukkit.getOnlinePlayers()) {
            User targetUser = userManager.loadOrGetUser(target);
            viewer.sendMessage("§e[DEBUG] target " + target.getName() + " tag=" +
                    (targetUser != null ? targetUser.getGuildTag() : "null"));
            if (targetUser == null || !targetUser.hasGuild()) {
                continue;
            }

            String entry = target.getName();
            String tag = targetUser.getGuildTag(); // np. "ABC"

            // ustawiamy prefix, ten sam TAG, różne kolory teamu
            String coloredTag;
            if (isSameGuild(viewerUser, targetUser)) {
                coloredTag = "§a[" + tag + "] ";
                greenTeam.setPrefix(coloredTag);
                greenTeam.addEntry(entry);
            } else {
                coloredTag = "§c[" + tag + "] ";
                redTeam.setPrefix(coloredTag);
                redTeam.addEntry(entry);
            }
        }

        viewer.setScoreboard(scoreboard);
    }

    private boolean isSameGuild(User a, User b) {
        if (a == null || b == null) return false;
        if (!a.hasGuild() || !b.hasGuild()) return false;
        return a.getGuildTag().equalsIgnoreCase(b.getGuildTag());
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }
}
