package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.ranking.DeathNotice;
import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerRespawnListener implements Listener {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private volatile String cachedGlobalSpawn;
    private final AtomicBoolean spawnRefreshInFlight = new AtomicBoolean();
    private final AtomicLong lastRefreshWarningNanos = new AtomicLong();

    public PlayerRespawnListener(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        refreshGlobalSpawn();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshGlobalSpawn, 20L, 20L);
    }

    // W pliku PlayerRespawnListener.java

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DeathNotice notice = plugin.getDeathNotices().remove(player.getUniqueId());
        if (sectorManager.isLockedSpawnBackend()) {
            plugin.getPlayerDeathSectors().remove(player.getUniqueId());
            event.setRespawnLocation(sectorManager.lockedSpawnLocation());
            showLocalNotice(player, notice);
            return;
        }

        String spawnDataJson = cachedGlobalSpawn;
        if (spawnDataJson == null || spawnDataJson.isEmpty()) {
            showLocalNotice(player, notice);
            return;
        }

        try {
            GlobalSpawnData spawnData = GlobalSpawnData.parse(spawnDataJson);
            String targetSector = spawnData.sector();
            if (sectorManager.getSectorByName(targetSector) == null) {
                plugin.getLogger().warning("Ignoring global respawn for unknown sector " + targetSector);
                return;
            }
            String deathSector = plugin.getPlayerDeathSectors().remove(player.getUniqueId());
            if (deathSector == null || deathSector.isBlank()) {
                deathSector = sectorManager.getSectorForLocation(
                        player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            }

            World world = Bukkit.getWorlds().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No world is loaded"));
            double x = spawnData.x();
            double y = spawnData.y();
            double z = spawnData.z();
            float yaw = spawnData.yaw();
            float pitch = spawnData.pitch();
            Location respawnLocation = new Location(world, x, y, z, yaw, pitch);

            if (targetSector.equals(deathSector)) {
                // Respawn lokalny - bez zmian
                event.setRespawnLocation(respawnLocation);
                showLocalNotice(player, notice);
                UUID playerUuid = player.getUniqueId();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && Bukkit.getPlayer(playerUuid) == player) {
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
                Map<String, String> transferKeys = Map.of(
                        "player:final_teleport_target:" + player.getUniqueId(), locationJson.toString(),
                        "player:is_respawning:" + player.getUniqueId(), notice == null ? "true" : notice.marker());
                sectorManager.transferPlayerAsync(player, targetSector, null, transferKeys);
            }
        } catch (RuntimeException exception) {
            showLocalNotice(player, notice);
            plugin.getLogger().warning("Could not prepare the global respawn for " + player.getName()
                    + ": " + exception.getMessage());
        }
    }

    private void showLocalNotice(Player player, DeathNotice notice) {
        if (notice == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(player.getUniqueId()) == player && !player.isDead()) notice.show(player);
        }, 8L);
    }

    private void refreshGlobalSpawn() {
        if (!spawnRefreshInFlight.compareAndSet(false, true)) return;
        redisManager.getAsync(RedisChannels.GLOBAL_SPAWN).whenComplete((spawnData, error) -> {
            try {
                if (!plugin.isEnabled()) return;
                if (error != null) {
                    long now = System.nanoTime();
                    long previous = lastRefreshWarningNanos.get();
                    if (now - previous >= 30_000_000_000L
                            && lastRefreshWarningNanos.compareAndSet(previous, now)) {
                        plugin.getLogger().warning("Could not refresh the global respawn location: "
                                + error.getMessage());
                    }
                    return;
                }
                cachedGlobalSpawn = spawnData;
            } finally {
                spawnRefreshInFlight.set(false);
            }
        });
    }
}
