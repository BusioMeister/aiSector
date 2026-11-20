package ai.aisector.ranking;

import ai.aisector.SectorPlugin;
import ai.aisector.guilds.Guild;
import ai.aisector.tab.NetworkRanking;
import ai.aisector.user.User;
import ai.aisector.utils.BukkitTaskScheduler;
import com.mongodb.client.model.Projections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CombatRanking {
    private final SectorPlugin plugin;

    public CombatRanking(SectorPlugin plugin) { this.plugin = plugin; }

    public Integer refundableLoss(Player victim, Player killer) {
        var users = plugin.getUserManager();
        User profile = users.getUser(victim);
        if (profile == null || users.isTransferPending(victim)) return null;
        User attacker = killer == null || killer.equals(victim) ? null : users.getUser(killer);
        return attacker == null || users.isTransferPending(killer) ? 0
                : Rating.loss(attacker.getRating(), profile.getRating());
    }

    private record Standing(UUID id, String name, int points, int deaths) {
        Standing(Player player, User profile) {
            this(player.getUniqueId(), player.getName(), profile.getRating(), profile.getDeaths());
        }
        NetworkRanking.Entry entry() {
            return new NetworkRanking.Entry(id.toString(), name, points, deaths);
        }
    }

    public CompletableFuture<Void> onDeath(Player victim, Player killer) {
        var users = plugin.getUserManager();
        User dead = users.getUser(victim);
        User attacker = killer == null || killer.equals(victim) ? null : users.getUser(killer);
        boolean ranked = dead != null && attacker != null
                && !users.isTransferPending(victim) && !users.isTransferPending(killer);
        Standing previousKiller = ranked ? new Standing(killer, attacker) : null;
        Standing previousVictim = ranked ? new Standing(victim, dead) : null;
        int gain = ranked ? Rating.award(previousKiller.points(), previousVictim.points()) : 0;
        int lost = ranked ? Rating.loss(previousKiller.points(), previousVictim.points()) : 0;
        Guild killerGuild = guild(killer, attacker);
        Guild victimGuild = guild(victim, dead);
        if (dead != null && !users.isTransferPending(victim)) {
            dead.setDeaths(Rating.increment(dead.getDeaths()));
            dead.setKillstreak(0);
            dead.setRating(dead.getRating() - lost);
        }
        if (attacker != null && !users.isTransferPending(killer)) {
            attacker.setKills(Rating.increment(attacker.getKills()));
            attacker.setKillstreak(Math.min(Rating.MAX, attacker.getKillstreak() + 1));
            attacker.setRating(attacker.getRating() + gain);
        }
        var victimSaved = dead != null && !users.isTransferPending(victim)
                ? users.saveCombatStats(victim, dead) : CompletableFuture.<Void>completedFuture(null);
        var killerSaved = attacker != null && !users.isTransferPending(killer)
                ? users.saveCombatStats(killer, attacker) : CompletableFuture.<Void>completedFuture(null);
        if (!ranked) return victimSaved;
        Standing afterKiller = new Standing(killer, attacker);
        Standing afterVictim = new Standing(victim, dead);
        String victimTag = displayTag(victimGuild);
        CompletableFuture.allOf(victimSaved, killerSaved).whenComplete((unused, error) -> {
            if (error != null) {
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (active(killer)) killer.sendActionBar(Component.text("Nie udało się potwierdzić zapisu rankingu.", NamedTextColor.RED));
                });
                return;
            }
            BukkitTaskScheduler.execute(plugin, () -> {
                plugin.getGuildManager().creditPvpRating(killerGuild, afterKiller.id(), victimGuild, afterVictim.id(), gain)
                        .whenComplete((credited, guildError) -> {
                            if (guildError != null) plugin.getLogger().warning("Could not credit guild PvP: " + guildError.getMessage());
                        });
            });
            positions(previousKiller, previousVictim, afterKiller, afterVictim)
                    .whenComplete((position, lookupError) -> BukkitTaskScheduler.execute(plugin, () -> {
                        if (active(killer) && !users.isTransferPending(killer)) {
                            animate(killer, victimTag, afterVictim.name(), gain,
                                    lookupError == null ? position : null);
                        }
                    }));
        });
        return victimSaved;
    }

    private boolean active(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private CompletableFuture<NetworkRanking.Movement> positions(Standing before, Standing oldVictim, Standing after, Standing newVictim) {
        var mongo = plugin.getMongoDBManager();
        return mongo.executeAsync(() -> {
            try (var cursor = mongo.getCollection("users").find().projection(
                    Projections.include("uuid", "name", "rating", "deaths")).maxTime(5, TimeUnit.SECONDS).cursor()) {
                return NetworkRanking.comparePositions(() -> cursor, before.entry(), oldVictim.entry(),
                        after.entry(), newVictim.entry());
            }
        });
    }

    private void animate(Player killer, String tag, String victim, int gain, NetworkRanking.Movement position) {
        int delta = position == null ? 0 : position.before() - position.after();
        String rank = position == null ? "" : delta > 0 ? "  ↑+" + delta : delta < 0 ? "  ↓−" + -delta : "  ↔0";
        Component subtitle = Component.text("+" + gain + " pkt", NamedTextColor.GOLD)
                .append(Component.text(rank, delta > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        killer.showTitle(Title.title(DeathNotice.name(tag, victim, NamedTextColor.YELLOW), subtitle,
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(400))));
    }

    public DeathNotice deathNotice(Player killer, Integer loss) {
        return new DeathNotice(displayTag(guild(killer, plugin.getUserManager().getUser(killer))), killer.getName(), loss);
    }

    public String announcement(Player killer, Player victim) {
        var users = plugin.getUserManager();
        return "§c⚔ " + display(killer, guild(killer, users.getUser(killer)))
                + " §czabił " + display(victim, guild(victim, users.getUser(victim))) + "§c.";
    }

    private Guild guild(Player player, User user) {
        if (player == null || user == null || !user.hasGuild()) return null;
        Guild guild = plugin.getGuildManager().getGuildCached(user.getGuildTag());
        return guild != null && guild.roleOf(player.getUniqueId()) != null ? guild : null;
    }

    private String display(Player player, Guild guild) {
        String tag = displayTag(guild);
        return (tag.isBlank() ? "" : "§6[" + tag + "] §c") + player.getName();
    }

    private static String displayTag(Guild guild) {
        return guild == null ? "" : guild.getTag().replaceAll("[^\\p{L}\\p{N}_-]", "");
    }
}
