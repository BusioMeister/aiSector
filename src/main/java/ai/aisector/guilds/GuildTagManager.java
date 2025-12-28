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

    public GuildTagManager(SectorPlugin plugin, UserManager userManager, GuildManager guildManager, RedisManager redisManager) {
        Bukkit.getLogger().info("[GuildTagManager] init");

        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
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

    // Główna metoda – wołamy ją dla WIDZA
    public void updateTagsFor(Player viewer) {
        Bukkit.getLogger().info("[GuildTagManager] updateTagsFor " + viewer.getName());
        viewer.sendMessage("§e[DEBUG] updateTagsFor");

        User viewerUser = userManager.loadOrGetUser(viewer);

        List<String> playerData = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            User targetUser = userManager.loadOrGetUser(target);

            if (targetUser == null || !targetUser.hasGuild()) {
                continue;
            }

            String color = isSameGuild(viewerUser, targetUser) ? "GREEN" : "RED";
            String data = target.getUniqueId().toString() + ":" + targetUser.getGuildTag() + ":" + color;
            playerData.add(data);
        }

        // WYSYŁAJ PAKIET
        String[] playersArray = playerData.toArray(new String[0]);
        GuildTagUpdatePacket packet = new GuildTagUpdatePacket(viewer.getUniqueId().toString(), playersArray);

        packetPublisher.publish("aisector:packet", packet);

        viewer.sendMessage("§e[DEBUG] Wysyłanie pakietu z " + playersArray.length + " tagami");
    }

    public void applyGuildTags(Player viewer, String[] playerData) {
        // czyść stare teamy
        for (Team team : scoreboard.getTeams()) {
            team.unregister();
        }

        // dla każdego gracza stwórz team i dodaj
        for (String data : playerData) {
            String[] parts = data.split(":");
            if (parts.length != 3) continue;

            String targetUuid = parts[0];
            String tag = parts[1];
            String color = parts[2];

            Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
            if (target == null) continue;

            String teamName = "g_" + tag;
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                String coloredTag = "GREEN".equals(color) ? "§a[" + tag + "] " : "§c[" + tag + "] ";
                team.setPrefix(coloredTag);
            }
            team.addEntry(target.getName());
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
