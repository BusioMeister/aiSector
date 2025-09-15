package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
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

    // W pliku PlayerRespawnListener.java

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        try (Jedis jedis = redisManager.getJedis()) {
            String spawnDataJson = jedis.get("aisector:global_spawn");
            if (spawnDataJson == null || spawnDataJson.isEmpty()) {
                return; // Cicha obsługa, jeśli spawn nie jest ustawiony
            }

            Document spawnData = Document.parse(spawnDataJson);
            String targetSector = spawnData.getString("sector");
            String deathSector = plugin.getPlayerDeathSectors().remove(player.getUniqueId());

            World world = Bukkit.getWorlds().get(0);
            double x = spawnData.getDouble("x");
            double y = spawnData.getDouble("y");
            double z = spawnData.getDouble("z");
            float yaw = spawnData.getDouble("yaw").floatValue();
            float pitch = spawnData.getDouble("pitch").floatValue();
            Location respawnLocation = new Location(world, x, y, z, yaw, pitch);

            if (targetSector.equals(deathSector)) {
                // Respawn lokalny - bez zmian
                event.setRespawnLocation(respawnLocation);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getWorldBorderManager().sendWorldBorder(player, sectorManager.getSectorByName(targetSector));
                    }
                }, 2L);
            } else {
                // Transfer na serwer spawnu
                JsonObject locationJson = new JsonObject();
                locationJson.addProperty("world", world.getName());
                locationJson.addProperty("x", x);
                locationJson.addProperty("y", y);
                locationJson.addProperty("z", z);
                locationJson.addProperty("yaw", yaw);
                locationJson.addProperty("pitch", pitch);
                jedis.setex("player:final_teleport_target:" + player.getUniqueId(), 60, locationJson.toString());

                // --- POCZĄTEK NOWEGO KODU ---
                // Ustawiamy w Redis sygnał, że ten transfer jest wynikiem śmierci.
                jedis.setex("player:is_respawning:" + player.getUniqueId(), 60, "true");
                // --- KONIEC NOWEGO KODU ---

                sectorManager.transferPlayer(player.getUniqueId(), targetSector);
            }
        }
    }
}