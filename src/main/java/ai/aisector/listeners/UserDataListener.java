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
    private final UserManager userManager;
    private final SectorPlugin plugin;

    public UserDataListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLoad(PlayerJoinEvent event) {
        // Ta metoda wczytuje dane z MongoDB do pamięci RAM.
        userManager.loadUser(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerApplyData(PlayerJoinEvent event) {
        // Ta metoda aplikuje wczytane stany (god, fly, etc.) do gracza.
        Player player = event.getPlayer();
        User user = userManager.getUser(player);
        if (user != null) {
            player.setInvulnerable(user.isGodMode());
            player.setAllowFlight(user.isFlying());
            if (user.isFlying()) player.setFlying(true);
            player.setWalkSpeed(user.getWalkSpeed());
            player.setFlySpeed(user.getFlySpeed());

            if (user.isVanished()) {
                // Ta logika również może tu zostać, jest powiązana z obiektem User
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
        // Ta metoda zapisuje dane z pamięci RAM do MongoDB.
        userManager.unloadUser(event.getPlayer());
    }
}