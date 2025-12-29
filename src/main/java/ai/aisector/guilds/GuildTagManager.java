package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.network.packet.GuildTagUpdatePacket;
import ai.aisector.redis.packet.PacketBus;
import ai.aisector.redis.packet.PacketEnvelope;
import ai.aisector.redis.packet.RedisPacketPublisher;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuildTagManager {

    private final SectorPlugin plugin;
    private final UserManager userManager;
    private final Scoreboard scoreboard;
    private final Team greenTeam;
    private final Team redTeam;
    private final RedisPacketPublisher packetPublisher;
    private enum Relation { SAME, ALLY, ENEMY, NONE }

    public GuildTagManager(SectorPlugin plugin, UserManager userManager, GuildManager guildManager, RedisManager redisManager) {
        Bukkit.getLogger().info("[GuildTagManager] init");

        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.packetPublisher = new RedisPacketPublisher(redisManager);  // <- DODAJ TO
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
    private String getRelationColor(User viewer, User target) {
        if (viewer == null || target == null) return "RED";
        if (!viewer.hasGuild() || !target.hasGuild()) return "RED";

        String viewerTag = viewer.getGuildTag();
        String targetTag = target.getGuildTag();
        if (viewerTag == null || targetTag == null) return "RED";

        if (viewerTag.equalsIgnoreCase(targetTag)) return "GREEN";

        Guild viewerGuild = plugin.getGuildManager().reloadGuild(viewerTag);
        if (viewerGuild != null && viewerGuild.getAlliedGuilds() != null
                && viewerGuild.getAlliedGuilds().contains(targetTag)) {
            return "BLUE";
        }

        return "RED";
    }



    // Główna metoda – wołamy ją dla WIDZA
    public void updateTagsFor(Player viewer) {
        User viewerUser = userManager.loadOrGetUser(viewer);

        List<String> playerData = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            User targetUser = userManager.loadOrGetUser(target);

            if (targetUser == null || !targetUser.hasGuild()) {
                continue;
            }

            String color = getRelationColor(viewerUser, targetUser);
            String data = target.getUniqueId().toString() + ":" + targetUser.getGuildTag() + ":" + color;
            playerData.add(data);
        }

        // WYSYŁAJ PAKIET
        String[] playersArray = playerData.toArray(new String[0]);
        GuildTagUpdatePacket packet = new GuildTagUpdatePacket(viewer.getUniqueId().toString(), playersArray);

        packetPublisher.publish("aisector:packet", packet);
    }
    public void refreshAllOnline() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateTagsFor(viewer);
        }
    }
    public void applyLocalTagsNow(Player viewer) {
        User viewerUser = userManager.loadOrGetUser(viewer);
        List<String> playerData = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            User targetUser = userManager.loadOrGetUser(target);
            if (targetUser == null || !targetUser.hasGuild()) continue;

            String color = isSameGuild(viewerUser, targetUser) ? "GREEN" : "RED";
            playerData.add(target.getUniqueId() + ":" + targetUser.getGuildTag() + ":" + color);
        }

        applyGuildTags(viewer, playerData.toArray(new String[0]));
    }



    public void applyGuildTags(Player viewer, String[] playerData) {
        Scoreboard sb = viewer.getScoreboard();
        if (sb == null) sb = Bukkit.getScoreboardManager().getMainScoreboard();

        // 1) Usuń wszystkich graczy z naszych teamów gildii
        for (Team t : new ArrayList<>(sb.getTeams())) {
            if (!t.getName().startsWith("g_")) continue;

            for (Player online : Bukkit.getOnlinePlayers()) {
                t.removeEntry(online.getName());
            }
        }

        // 2) Usuń puste teamy gildii (opcjonalne, ale czyści nazwy)
        for (Team t : new ArrayList<>(sb.getTeams())) {
            if (t.getName().startsWith("g_") && t.getEntries().isEmpty()) {
                t.unregister();
            }
        }

        // 3) Dodaj aktualne tagi
        for (String data : playerData) {
            String[] parts = data.split(":");
            if (parts.length != 3) continue;

            String targetUuid = parts[0];
            String tag = parts[1];
            String color = parts[2];

            Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
            if (target == null) continue;

            String teamName = "g_" + tag;
            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }

            String prefix;
            if ("GREEN".equals(color)) {
                prefix = "§a[" + tag + "] ";
            } else if ("BLUE".equals(color)) {
                prefix = "§9[" + tag + "] ";
            } else {
                prefix = "§c[" + tag + "] ";
            }

            team.setPrefix(prefix);
            team.addEntry(target.getName());
        }
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
