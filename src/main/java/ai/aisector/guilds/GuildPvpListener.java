package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class GuildPvpListener implements Listener {

    private final GuildManager guildManager;
    private final UserManager userManager;

    public GuildPvpListener(SectorPlugin plugin) {
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        User vUser = userManager.loadOrGetUser(victim);
        User aUser = userManager.loadOrGetUser(attacker);
        if (vUser == null || aUser == null) return;

        String vTag = vUser.getGuildTag();
        String aTag = aUser.getGuildTag();
        if (vTag == null || aTag == null) return;

        Guild vGuild = guildManager.getGuild(vTag);
        Guild aGuild = guildManager.getGuild(aTag);
        if (vGuild == null || aGuild == null) return;

        // PvP wewnątrz gildii
        if (vTag.equalsIgnoreCase(aTag)) {
            if (!vGuild.isFriendlyFireGuild()) {
                event.setCancelled(true);
            }
            return;
        }

        // PvP między sojuszami – działa tylko jeśli obie gildie mają FF włączone
        if (vGuild.getAlliedGuilds().contains(aTag) && aGuild.getAlliedGuilds().contains(vTag)) {
            if (!(vGuild.isFriendlyFireAllies() && aGuild.isFriendlyFireAllies())) {
                event.setCancelled(true);
            }
        }
    }
}
