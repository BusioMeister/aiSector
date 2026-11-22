package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.ranking.DeathNotice;
import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
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
    private record RespawnTransfer(Player player, String sector, Location destination,
                                   Map<String, String> keys, DeathNotice notice) {}
    private final Map<UUID, RespawnTransfer> pendingRespawns = new java.util.HashMap<>();

    public PlayerRespawnListener(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        Bukkit.getWorlds().forEach(this::enableImmediateRespawn);
        refreshGlobalSpawn();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshGlobalSpawn, 20L, 20L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        enableImmediateRespawn(event.getWorld());
    }

    private void enableImmediateRespawn(World world) {
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Let Paper finish the death (drops and stats) first. Some clients defer
        // their vanilla respawn request while another screen, such as chat, is open.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(player.getUniqueId()) == player && player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

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
                showLocalNotice(player, notice);
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
                pendingRespawns.put(player.getUniqueId(), new RespawnTransfer(player, targetSector,
                        respawnLocation, transferKeys, notice));
            }
        } catch (RuntimeException exception) {
            showLocalNotice(player, notice);
            plugin.getLogger().warning("Could not prepare the global respawn for " + player.getName()
                    + ": " + exception.getMessage());
        }
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        RespawnTransfer transfer = pendingRespawns.remove(player.getUniqueId());
        if (transfer == null || transfer.player() != player) return;
        try {
            // Capture the new, post-death inventory, never the old inventory cached on Spawn.
            String snapshot = plugin.getUserManager().serializePlayerDataForTransfer(player, transfer.destination());
            sectorManager.transferPlayerAsync(player, transfer.sector(), snapshot, transfer.keys()).whenComplete((ignored, error) -> {
                if (error != null) ai.aisector.utils.BukkitTaskScheduler.execute(plugin, () -> showLocalNotice(player, transfer.notice()));
            });
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not capture post-death inventory for " + player.getName() + ": " + error.getMessage());
            showLocalNotice(player, transfer.notice());
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        pendingRespawns.remove(event.getPlayer().getUniqueId());
    }

    private void showLocalNotice(Player player, DeathNotice notice) {
        if (notice == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(player.getUniqueId()) == player && !player.isDead()) notice.show(player, plugin.getCombatTitle());
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
