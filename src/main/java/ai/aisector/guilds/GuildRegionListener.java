package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class GuildRegionListener implements Listener {

    private final SectorPlugin plugin;
    private final GuildManager guildManager;
    private final UserManager userManager;

    private static final int RADIUS = 11; // 11 kratek od środka

    public GuildRegionListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cNie możesz budować na terenie tej gildii.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cNie możesz niszczyć na terenie tej gildii.");
        }
    }

    private boolean canBuild(Player player, Location loc) {
        // jeśli blok nie leży w żadnym terenie gildii -> pozwól
        Guild ownerGuild = findGuildRegionOwner(loc);
        if (ownerGuild == null) return true;

        // jeśli gracz jest członkiem tej gildii -> pozwól
        User u = userManager.loadOrGetUser(player);
        if (u != null && u.hasGuild() && ownerGuild.getTag().equalsIgnoreCase(u.getGuildTag())) {
            return true;
        }

        return false;
    }

    private Guild findGuildRegionOwner(Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;

        for (Guild g : guildManager.getAllGuilds()) {
            if (g.getHomeWorld() == null) continue;
            if (!g.getHomeWorld().equalsIgnoreCase(w.getName())) continue;

            double dx = loc.getX() - g.getHomeX();
            double dz = loc.getZ() - g.getHomeZ();

            // kwadrat 11x11 “od środka” (czyli +/- 11)
            if (Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS) {
                return g;
            }
        }
        return null;
    }
}
