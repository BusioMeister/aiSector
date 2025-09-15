package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VanishPlayerListener implements Listener {

    private final VanishManager vanishManager;
    private final UserManager userManager;

    public VanishPlayerListener(SectorPlugin plugin, VanishManager vanishManager) {
        this.vanishManager = vanishManager;
        this.userManager = plugin.getUserManager();
    }

    // Używamy priorytetu HIGH, aby uruchomić się PO UserDataListener
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        User user = userManager.getUser(player);

        if (user == null) return;

        // Krok 1: Najpierw ukryj przed dołączającym graczem adminów, którzy już są w vanish.
        vanishManager.handlePlayerJoin(player);

        // Krok 2: Teraz podejmij ostateczną decyzję o stanie vanish dołączającego gracza
        // na podstawie danych wczytanych z MongoDB.
        if (user.isVanished()) {
            vanishManager.vanish(player);
        } else {
            vanishManager.unvanish(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        // Gdy gracz wychodzi, zawsze usuwaj go z listy vanish "na żywo" na tym serwerze.
        // Stan w MongoDB jest już zapisany przez UserDataListener.
        vanishManager.unvanish(event.getPlayer());
    }
}