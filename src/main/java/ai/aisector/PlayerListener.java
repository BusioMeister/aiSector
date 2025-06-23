package ai.aisector;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import redis.clients.jedis.Jedis;

import java.util.Objects;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final WorldBorderManager borderManager = new WorldBorderManager();

    public PlayerListener(SectorManager sectorManager, RedisManager redisManager) {
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();

        String sectorName = sectorManager.getSectorForLocation(x,z);
            if (Objects.equals(sectorName, ""))return;

        SectorData sectorData = sectorManager.calculateSectorData(sectorName);
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        String previousSectorId = sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());
        Location location = Direction.fromLocations(from,to).add(to.clone(),3);

        long start = System.currentTimeMillis();


        if (!previousSectorId.equals(newSectorId)) {
            try (Jedis jedis = redisManager.getJedis()) {
                String key = "player:data:" + playerId;
                String data = PlayerDataSerializer.serialize(event.getPlayer(),location);
                jedis.set(key, data);
                jedis.expire(key, 60 * 5); // Dane wygasają po 5 minutach
            }

            long end = System.currentTimeMillis();
            System.out.println("Gracz " + event.getPlayer().getName() + " przeszedł z sektora " + previousSectorId +
                    " do sektora " + newSectorId + " " + (end - start));

            sectorManager.transferPlayer(playerId, newSectorId);
        }

    }

}
