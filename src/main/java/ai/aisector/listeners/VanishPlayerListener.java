package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.player.VanishManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;

public class VanishPlayerListener implements Listener {

    private final SectorPlugin plugin;
    private final VanishManager vanishManager;
    private final UserManager userManager;

    public VanishPlayerListener(SectorPlugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
        this.userManager = plugin.getUserManager();
    }

    // Używamy priorytetu HIGH, aby uruchomić się PO UserDataListener
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID playerId = player.getUniqueId();
        userManager.loadUserAsync(player).whenComplete((user, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (!player.isOnline() || Bukkit.getPlayer(playerId) != player) return;
                    if (error != null || user == null) {
                        plugin.getLogger().warning("Could not reconcile vanish state for " + player.getName()
                                + (error == null ? ": profile unavailable" : ": " + error.getMessage()));
                        return;
                    }
                    // Profile loading is async: reconcile only after its cache entry exists.
                    vanishManager.handlePlayerJoin(player);
                    if (user.isVanished()) vanishManager.vanish(player);
                    else vanishManager.unvanish(player);
                }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        // Disconnect removes local Bukkit state but must not publish an UNVANISH update:
        // the persisted vanish flag remains authoritative for the next backend/session.
        vanishManager.handlePlayerQuit(event.getPlayer());
    }
}
