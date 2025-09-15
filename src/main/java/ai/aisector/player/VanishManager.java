package ai.aisector.player;

import ai.aisector.SectorPlugin;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VanishManager {

    private final SectorPlugin plugin;
    private final VanishTagManager tagManager; // <-- NOWE POLE
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.tagManager = new VanishTagManager(); // Inicjalizujemy tag manager
    }

    public static boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    // Nowa metoda do pobierania listy ukrytych graczy
    public static Set<Player> getVanishedPlayers() {
        return vanishedPlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public void vanish(Player admin) {
        vanishedPlayers.add(admin.getUniqueId());
        tagManager.setVanishedTag(admin.getName()); // <-- APLIKUJ TAG
        broadcastVanishState(admin, "VANISH"); // <-- ROZSYŁAJ INFO

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                onlinePlayer.hidePlayer(plugin, admin);
            }
        }
    }

    public void unvanish(Player admin) {
        vanishedPlayers.remove(admin.getUniqueId());
        tagManager.removeVanishedTag(admin.getName()); // <-- USUŃ TAG
        broadcastVanishState(admin, "UNVANISH"); // <-- ROZSYŁAJ INFO

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, admin);
        }
    }

    public void handlePlayerJoin(Player joiningPlayer) {
        // Pokazujemy dołączającemu adminowi tagi graczy, którzy już są w vanish
        if (joiningPlayer.hasPermission("aisector.command.vanish.see")) {
            tagManager.updateTagsForJoiningStaff(joiningPlayer);
        }

        // Ukrywamy przed dołączającym graczem adminów będących w vanish
        for (UUID vanishedUUID : vanishedPlayers) {
            Player vanishedAdmin = Bukkit.getPlayer(vanishedUUID);
            if (vanishedAdmin != null) {
                if (!joiningPlayer.hasPermission("aisector.command.vanish.see")) {
                    joiningPlayer.hidePlayer(plugin, vanishedAdmin);
                }
            }
        }
    }

    // Nowa metoda do rozsyłania statusu po sieci
    private void broadcastVanishState(Player player, String action) {
        JsonObject data = new JsonObject();
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", player.getName()); // Przesyłamy też nick
        data.addProperty("action", action);

        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            jedis.publish("aisector:vanish_update", data.toString());
        }
    }
}