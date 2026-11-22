package ai.aisector.ranking;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathAnnouncements {
    public static final String CHANNEL = "aisector:death:announcement";
    private static final String TAG = "[\\p{L}\\p{N}_-]{0,32}";
    private static final String NICK = "[A-Za-z0-9_]{1,16}";

    private DeathAnnouncements() {}

    private enum Reason {
        LOGOUT("logout"), DROWN("drown"), STARVE("starve"), FIRE("fire"),
        LAVA("lava"), FALL("fall"), VOID("void"), OTHER("other");

        final String key;
        Reason(String key) { this.key = "death-announcement." + key; }
    }

    public static void publish(SectorPlugin plugin, CombatRanking ranking, PlayerDeathEvent event, Player killer,
                               boolean creditedCombatFall) {
        Player victim = event.getEntity();
        boolean playerKill = killer != null && !killer.equals(victim);
        Reason reason = plugin.isCombatLogoutDeath(victim) ? Reason.LOGOUT : environment(event);
        String message;
        if (playerKill && (reason == Reason.OTHER || reason == Reason.FALL && creditedCombatFall)) {
            message = ranking.announcementPayload(killer, victim);
        } else {
            message = reason.name() + "|" + CombatRanking.memberTag(plugin, victim) + "|" + victim.getName()
                    + "|" + (playerKill ? CombatRanking.memberTag(plugin, killer) : "")
                    + "|" + (playerKill ? killer.getName() : "");
        }
        plugin.getRedisManager().publishAsync(CHANNEL, message).whenComplete((subscribers, error) -> {
            // On a Redis error the publish may already have reached subscribers. Avoid
            // repeating it when the result is uncertain.
            if (error != null) {
                plugin.getLogger().warning("Could not confirm global death announcement: " + error.getMessage());
            } else if (subscribers == null || subscribers == 0) {
                BukkitTaskScheduler.execute(plugin, () -> broadcast(plugin, message));
            }
        });
    }

    public static boolean isCombatFall(PlayerDeathEvent event) {
        String damage = event.getDamageSource().getDamageType().getKey().getKey();
        return damage.equals("fall") || damage.equals("stalagmite");
    }

    private static Reason environment(PlayerDeathEvent event) {
        return switch (event.getDamageSource().getDamageType().getKey().getKey()) {
            case "drown" -> Reason.DROWN;
            case "starve" -> Reason.STARVE;
            case "in_fire", "on_fire", "campfire", "hot_floor" -> Reason.FIRE;
            case "lava" -> Reason.LAVA;
            case "fall", "stalagmite", "fly_into_wall" -> Reason.FALL;
            case "out_of_world" -> Reason.VOID;
            default -> Reason.OTHER;
        };
    }

    public static void broadcast(SectorPlugin plugin, String message) {
        if (message == null || message.length() > 144) return;
        if (message.startsWith("[KILL]")) {
            CombatRanking.broadcastAnnouncement(plugin, message.substring(6));
            return;
        }
        String[] fields = message.split("\\|", -1);
        if (fields.length != 5 || !fields[1].matches(TAG) || !fields[2].matches(NICK)
                || !fields[3].matches(TAG) || !fields[4].isEmpty() && !fields[4].matches(NICK)) return;
        Reason reason;
        try { reason = Reason.valueOf(fields[0]); }
        catch (IllegalArgumentException invalid) { return; }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String ownTag = CombatRanking.memberTag(plugin, viewer);
            String victim = CombatRanking.display(fields[1], fields[2], ownTag);
            String assisted = fields[4].isEmpty() ? "" : Messages.text("death-announcement.credited",
                    CombatRanking.display(fields[3], fields[4], ownTag));
            viewer.sendMessage(Messages.text(reason.key, victim, assisted));
        }
    }
}
