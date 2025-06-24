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
    private final WorldBorderManager borderManager;

    public PlayerListener(SectorManager sectorManager, RedisManager redisManager, WorldBorderManager borderManager) {
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String previousSectorId = sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());

        if (!previousSectorId.equals(newSectorId)) {
            Location location = Direction.fromLocations(from, to).add(to.clone(), 3);
            long start = System.currentTimeMillis();

            try (Jedis jedis = redisManager.getJedis()) {
                String key = "player:data:" + playerId;
                String data = PlayerDataSerializer.serialize(player, location);
                jedis.set(key, data);
                jedis.expire(key, 60 * 5); // wygasa po 5 minutach
            }

            long end = System.currentTimeMillis();
            System.out.println("Gracz " + player.getName() + " przeszedł z sektora " + previousSectorId +
                    " do sektora " + newSectorId + " (" + (end - start) + "ms)");

            sectorManager.transferPlayer(playerId, newSectorId);

            SectorData sectorData = sectorManager.calculateSectorData(newSectorId);
            borderManager.sendWorldBorder(player,
                    sectorData.getCenterX(),
                    sectorData.getCenterZ(),
                    sectorData.getSize() + 0.5);
        }
    }
}
