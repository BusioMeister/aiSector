package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.SectorManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;

    public PlayerDeathListener(SectorPlugin plugin, SectorManager sectorManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        String sectorName = sectorManager.getSectorForLocation(deathLocation.getBlockX(), deathLocation.getBlockZ());

        // Zapisz sektor, w którym gracz zginął, aby użyć go przy respawnie
        plugin.getPlayerDeathSectors().put(player.getUniqueId(), sectorName);
    }
}