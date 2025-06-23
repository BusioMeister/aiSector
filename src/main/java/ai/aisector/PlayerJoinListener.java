package ai.aisector;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

import java.util.Objects;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final WorldBorderManager borderManager = new WorldBorderManager();


    public PlayerJoinListener(JavaPlugin plugin, SectorManager sectorManager, RedisManager redisManager) {
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String key = "player:data:" + playerId;

        try (Jedis jedis = redisManager.getJedis()) {
            String data = jedis.get(key);
            if (data != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    PlayerDataSerializer.deserialize(event.getPlayer(), data);
                    String sectorName = sectorManager.getSectorForLocation(event.getPlayer().getLocation().getBlockX(),event.getPlayer().getLocation().getBlockZ());
                    if (Objects.equals(sectorName, ""))return;

                    SectorData sectorData = sectorManager.calculateSectorData(sectorName);
                    borderManager.sendWorldBorder(event.getPlayer(),sectorData.getCenterX(),sectorData.getCenterZ(),sectorData.getSize()+3);
                    plugin.getLogger().info("Dane gracza " + event.getPlayer().getName() + " zostały załadowane.");
                }, 2L); // Odczekanie 1 sekundy po dołączeniu
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas wczytywania danych gracza: " + e.getMessage());
        }
    }
}
