package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserDataListener implements Listener {
    private final SectorPlugin plugin;
    private final UserManager userManager;

    public UserDataListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        userManager.loadUser(player);
        User user = userManager.getUser(player);
        if (user != null) {
            // Aplikujemy wczytane stany
            player.setInvulnerable(user.isGodMode());
            player.setAllowFlight(user.isFlying());
            if (user.isFlying()) player.setFlying(true);
            player.setWalkSpeed(user.getWalkSpeed());
            player.setFlySpeed(user.getFlySpeed());

            // Obsługa vanisha
            if (user.isVanished()) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                        onlinePlayer.hidePlayer(plugin, player);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        userManager.unloadUser(event.getPlayer());
    }
}