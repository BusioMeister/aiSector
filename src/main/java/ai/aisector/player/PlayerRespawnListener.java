package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import redis.clients.jedis.Jedis;

public class PlayerRespawnListener implements Listener {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;

    public PlayerRespawnListener(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        try (Jedis jedis = redisManager.getJedis()) {
            String spawnDataJson = jedis.get("aisector:global_spawn");
            if (spawnDataJson == null || spawnDataJson.isEmpty()) {
                return;
            }

            Document spawnData = Document.parse(spawnDataJson);
            String targetSector = spawnData.getString("sector");
            String deathSector = plugin.getPlayerDeathSectors().remove(player.getUniqueId());

            if (targetSector.equals(deathSector)) {
                // PRZYPADEK 1: Gracz zginął na sektorze spawnu -> TELEPORT LOKALNY
                World world = Bukkit.getWorlds().get(0);
                double x = spawnData.getDouble("x");
                double y = spawnData.getDouble("y");
                double z = spawnData.getDouble("z");
                float yaw = spawnData.getDouble("yaw").floatValue();
                float pitch = spawnData.getDouble("pitch").floatValue();
                Location respawnLocation = new Location(world, x, y, z, yaw, pitch);

                event.setRespawnLocation(respawnLocation);

            } else {
                // PRZYPADEK 2: Gracz zginął na innym sektorze -> TRANSFER
                jedis.setex("player:respawn:" + player.getUniqueId(), 10, "true");
                sectorManager.transferPlayer(player.getUniqueId(), targetSector);
            }
        }
    }
}