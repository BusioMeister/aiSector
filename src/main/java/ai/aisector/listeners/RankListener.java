package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.PermissionManager;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class RankListener implements Listener {

    private final RankManager rankManager;
    private final PermissionManager permissionManager;

    public RankListener(SectorPlugin plugin) {
        this.rankManager = plugin.getRankManager();
        this.permissionManager = plugin.getPermissionManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!rankManager.playerExistsInRankDB(player.getUniqueId())) {
            Rank defaultRank = rankManager.getDefaultRank();
            if (defaultRank != null) {
                rankManager.setPlayerRank(player.getUniqueId(), defaultRank, player.getName());
            }
        }

        permissionManager.applyPlayerPermissions(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        permissionManager.removePlayerPermissions(event.getPlayer());
    }
}