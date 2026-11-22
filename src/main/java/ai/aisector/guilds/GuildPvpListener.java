package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustByEntityEvent;

public class GuildPvpListener implements Listener {

    private final GuildManager guildManager;
    private final UserManager userManager;

    public GuildPvpListener(SectorPlugin plugin) {
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = source(event.getDamageSource().getCausingEntity());
        if (attacker == null) attacker = source(event.getDamager());
        if (attacker != null && blocked(victim, attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Player attacker = source(event.getCombuster());
            if (attacker != null && blocked(victim, attacker)) event.setCancelled(true);
        }
    }

    private static Player source(Entity entity) {
        if (entity instanceof Player player) return player;
        return entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    private boolean blocked(Player victim, Player attacker) {
        if (victim.equals(attacker)) return false;
        if (!guildManager.isReady()) return true;

        User vUser = userManager.loadOrGetUser(victim);
        User aUser = userManager.loadOrGetUser(attacker);
        if (vUser == null || aUser == null) {
            // Match the fail-closed policy used while the guild cache is loading.
            return true;
        }

        String vTag = vUser.getGuildTag();
        String aTag = aUser.getGuildTag();
        Guild vGuild = vTag == null ? null : guildManager.getGuild(vTag);
        Guild aGuild = aTag == null ? null : guildManager.getGuild(aTag);
        if (GuildPvpPolicy.hasUnresolvedGuild(vTag, vGuild)
                || GuildPvpPolicy.hasUnresolvedGuild(aTag, aGuild)
                || vGuild != null && guildManager.permissionsRefreshing(vGuild.getTag())
                || aGuild != null && guildManager.permissionsRefreshing(aGuild.getTag())) return true;
        if (vGuild == null || aGuild == null || vGuild.roleOf(victim.getUniqueId()) == null
                || aGuild.roleOf(attacker.getUniqueId()) == null) return false;

        if (vTag.equalsIgnoreCase(aTag)) return !vGuild.isFriendlyFireGuild();
        return GuildManager.containsTag(vGuild.getAlliedGuilds(), aTag)
                && GuildManager.containsTag(aGuild.getAlliedGuilds(), vTag)
                && !(vGuild.isFriendlyFireAllies() && aGuild.isFriendlyFireAllies());
    }
}
