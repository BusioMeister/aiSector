package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

public class GuildPvpListener implements Listener {

    private final GuildManager guildManager;
    private final UserManager userManager;

    public GuildPvpListener(SectorPlugin plugin) {
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker;
        if (event.getDamager() instanceof Player directAttacker) {
            attacker = directAttacker;
        } else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (!(shooter instanceof Player projectileAttacker)) return;
            attacker = projectileAttacker;
        } else {
            return;
        }
        if (!guildManager.isReady()) {
            event.setCancelled(true);
            return;
        }

        User vUser = userManager.loadOrGetUser(victim);
        User aUser = userManager.loadOrGetUser(attacker);
        if (vUser == null || aUser == null) {
            // Match the fail-closed policy used while the guild cache is loading.
            event.setCancelled(true);
            return;
        }

        String vTag = vUser.getGuildTag();
        String aTag = aUser.getGuildTag();
        Guild vGuild = vTag == null ? null : guildManager.getGuild(vTag);
        Guild aGuild = aTag == null ? null : guildManager.getGuild(aTag);
        if (GuildPvpPolicy.hasUnresolvedGuild(vTag, vGuild)
                || GuildPvpPolicy.hasUnresolvedGuild(aTag, aGuild)) {
            event.setCancelled(true);
            return;
        }
        if (vTag == null || aTag == null) return;

        // PvP wewnątrz gildii
        if (vTag.equalsIgnoreCase(aTag)) {
            if (!vGuild.isFriendlyFireGuild()) {
                event.setCancelled(true);
            }
            return;
        }

        // PvP między sojuszami – działa tylko jeśli obie gildie mają FF włączone
        if (GuildManager.containsTag(vGuild.getAlliedGuilds(), aTag)
                && GuildManager.containsTag(aGuild.getAlliedGuilds(), vTag)) {
            if (!(vGuild.isFriendlyFireAllies() && aGuild.isFriendlyFireAllies())) {
                event.setCancelled(true);
            }
        }
    }
}
