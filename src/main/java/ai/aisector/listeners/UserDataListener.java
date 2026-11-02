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

public final class UserDataListener implements Listener {
    private final UserManager userManager;

    public UserDataListener(SectorPlugin plugin) {
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLoad(PlayerJoinEvent event) {
        userManager.loadUserAsync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        User user = userManager.getUser(player);
        if (user != null && user.getSessionStartMillis() > 0L) {
            long sessionSeconds = (System.currentTimeMillis() - user.getSessionStartMillis()) / 1_000L;
            user.addPlayTime(sessionSeconds);
        }
        userManager.unloadUser(player);
    }
}
