package ai.aisector.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    private final JavaPlugin plugin;
    // Lokalna, szybka kopia znikniętych graczy na tym serwerze
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    // Ukryj gracza przed innymi
    public void vanish(Player admin) {
        vanishedPlayers.add(admin.getUniqueId());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("aisector.command.vanish")) {
                onlinePlayer.hidePlayer(plugin, admin);
            }
        }
    }

    // Pokaż gracza innym
    public void unvanish(Player admin) {
        vanishedPlayers.remove(admin.getUniqueId());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, admin);
        }
    }

    // Logika dla dołączającego gracza (aby nie widział znikniętych)
    public void handlePlayerJoin(Player joiningPlayer) {
        // Jeśli dołączający gracz nie jest adminem, ukryj przed nim wszystkich znikniętych
        if (!joiningPlayer.hasPermission("aisector.command.vanish")) {
            for (UUID vanishedUUID : vanishedPlayers) {
                Player vanishedAdmin = Bukkit.getPlayer(vanishedUUID);
                if (vanishedAdmin != null) {
                    joiningPlayer.hidePlayer(plugin, vanishedAdmin);
                }
            }
        }
    }
}