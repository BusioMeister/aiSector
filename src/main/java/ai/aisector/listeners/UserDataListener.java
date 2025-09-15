package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserDataListener implements Listener {
    private final UserManager userManager;

    public UserDataListener(SectorPlugin plugin) {
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLoad(PlayerJoinEvent event) {
        userManager.loadUser(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerApplyData(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        User user = userManager.getUser(player);
        if (user != null) {
            player.setInvulnerable(user.isGodMode());
            player.setAllowFlight(user.isFlying());
            if (user.isFlying()) player.setFlying(true);
            player.setWalkSpeed(user.getWalkSpeed());
            player.setFlySpeed(user.getFlySpeed());
            // USUNIĘTO logikę vanish - zajmie się nią inny listener
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        userManager.unloadUser(event.getPlayer());
    }
}