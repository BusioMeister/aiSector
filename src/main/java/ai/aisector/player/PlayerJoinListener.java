package ai.aisector.player;

import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.database.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final WorldBorderManager borderManager;

    public PlayerJoinListener(JavaPlugin plugin, SectorManager sectorManager, RedisManager redisManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String key = "player:data:" + playerId;


        try (Jedis jedis = redisManager.getJedis()) {
            String data = jedis.get(key);
            if (data != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    PlayerDataSerializer.deserialize(player, data);

                    Bukkit.getLogger().info("Player " + player.getName() + " joined at: " + player.getLocation());


                    String sectorName = sectorManager.getSectorForLocation(
                            player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ());

                    if (sectorName == null || sectorName.isEmpty()) {
                        plugin.getLogger().warning("Gracz " + player.getName() + " nie znajduje się w żadnym sektorze.");
                        return;
                    }

                    Sector sector = sectorManager.getSector(
                            player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ());

                    if (sector == null) {
                        plugin.getLogger().warning("Nie znaleziono sektora dla gracza " + player.getName());
                        return;
                    }

                    borderManager.sendWorldBorder(player, sector);


                    plugin.getLogger().info("Dane gracza " + player.getName() + " zostały załadowane i ustawiono border sektora " + sectorName);

                }, 2L);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas wczytywania danych gracza: " + e.getMessage());
        }
    }

}
