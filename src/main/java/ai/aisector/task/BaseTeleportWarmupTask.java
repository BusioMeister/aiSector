package ai.aisector.task;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class BaseTeleportWarmupTask extends BukkitRunnable {

    private static final Gson GSON = new Gson();

    private final Player player;
    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private final UserManager userManager;

    private final Location startLocation;

    private final String targetSector;
    private final Location targetLocation;
    private final String targetWorldName;

    private final int warmupSeconds;
    private final String bossBarTitlePrefix;
    private final String actionBarTextPrefix;
    private final String successMessage;

    private final String redisKey;
    private final int redisTtlSeconds;

    private int timeLeft;
    private final BossBar bossBar;

    public BaseTeleportWarmupTask(Player player,
                                  Location targetLocation,
                                  String targetSector,
                                  int warmupSeconds,
                                  String bossBarTitlePrefix,
                                  String actionBarTextPrefix,
                                  String successMessage,
                                  String redisKey,
                                  int redisTtlSeconds,
                                  SectorPlugin plugin) {
        this(player, targetLocation, targetLocation.getWorld() == null ? null : targetLocation.getWorld().getName(),
                targetSector, warmupSeconds, bossBarTitlePrefix, actionBarTextPrefix, successMessage,
                redisKey, redisTtlSeconds, plugin);
    }

    public BaseTeleportWarmupTask(Player player, Location targetLocation, String targetWorldName,
                                  String targetSector, int warmupSeconds, String bossBarTitlePrefix,
                                  String actionBarTextPrefix, String successMessage, String redisKey,
                                  int redisTtlSeconds, SectorPlugin plugin) {

        this.player = player;
        this.startLocation = player.getLocation();

        this.targetSector = targetSector;
        this.targetLocation = targetLocation;
        this.targetWorldName = targetWorldName;

        this.warmupSeconds = warmupSeconds;
        this.timeLeft = warmupSeconds;

        this.bossBarTitlePrefix = bossBarTitlePrefix;
        this.actionBarTextPrefix = actionBarTextPrefix;
        this.successMessage = successMessage;

        this.redisKey = redisKey;
        this.redisTtlSeconds = redisTtlSeconds;

        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
        this.sectorManager = plugin.getSectorManager();
        this.userManager = plugin.getUserManager();

        this.bossBar = Bukkit.createBossBar(
                bossBarTitlePrefix + timeLeft,
                BarColor.GREEN,
                BarStyle.SOLID
        );
    }

    public void start() {
        if (!TeleportWarmupRegistry.registerWarmup(player, this)) {
            bossBar.removeAll();
            player.sendMessage(Messages.text("base-teleport-warmup-task.poprzednia-teleportacja-jest-juz-w-trakcie-wykonywania"));
            return;
        }
        try {
            bossBar.addPlayer(player);
            runTaskTimer(plugin, 0L, 20L);
        } catch (RuntimeException exception) {
            TeleportWarmupRegistry.unregister(player, this);
            bossBar.removeAll();
            throw exception;
        }
    }

    @Override
    public void run() {
        if (!isCurrentSession()) {
            cancel();
            return;
        }

        if (!player.getWorld().equals(startLocation.getWorld())) {
            player.sendMessage(Messages.text("base-teleport-warmup-task.teleportacja-anulowana-zmieniles-swiat"));
            cancel();
            return;
        }
        if (player.getLocation().distanceSquared(startLocation) > 1.0) {
            player.sendMessage(Messages.text("base-teleport-warmup-task.teleportacja-anulowana-ruszyles-sie"));
            cancel();
            return;
        }

        if (timeLeft <= 0) {
            teleportNow();
            return;
        }

        bossBar.setTitle(bossBarTitlePrefix + timeLeft);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) timeLeft / (double) warmupSeconds)));

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                Messages.text("base-teleport-warmup-task.s", actionBarTextPrefix + timeLeft)
        ));

        timeLeft--;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        try {
            super.cancel();
        } catch (IllegalStateException ignored) {
            // The task may be cancelled before the scheduler starts it.
        } finally {
            TeleportWarmupRegistry.unregister(player, this);
            bossBar.removeAll();
        }
    }

    private void teleportNow() {
        Object operationToken = TeleportWarmupRegistry.beginPendingOperation(player, this);
        if (operationToken == null) {
            cancel();
            return;
        }
        try {
            super.cancel();
        } catch (IllegalStateException ignored) {
            // The scheduled warmup has already ended.
        }
        bossBar.removeAll();

        if (targetSector == null || targetSector.isBlank()) {
            player.sendMessage(Messages.text("base-teleport-warmup-task.nie-udalo-sie-ustalic-sektora-docelowego"));
            TeleportWarmupRegistry.completePendingOperation(player, operationToken);
            return;
        }

        String currentSector = sectorManager.getSectorForLocation(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ()
        );

        if (targetSector.equals(currentSector)) {
            if (targetLocation.getWorld() == null) {
                player.sendMessage(Messages.text("base-teleport-warmup-task.wystapil-blad-swiat-docelowy-nie-istnieje"));
                TeleportWarmupRegistry.completePendingOperation(player, operationToken);
                return;
            }
            try {
                player.teleportAsync(targetLocation).whenComplete((teleported, error) -> {
                    boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        try {
                            if (!isCurrentSession()) return;
                            if (error == null && Boolean.TRUE.equals(teleported)) {
                                player.sendMessage(successMessage);
                                plugin.getWorldBorderManager().sendWorldBorder(player,
                                        sectorManager.getSectorByName(targetSector));
                            } else player.sendMessage(Messages.text("base-teleport-warmup-task.nie-udalo-sie-wykonac-teleportacji"));
                        } finally {
                            TeleportWarmupRegistry.completePendingOperation(player, operationToken);
                        }
                    });
                    if (!scheduled) TeleportWarmupRegistry.completePendingOperation(player, operationToken);
                });
            } catch (RuntimeException exception) {
                player.sendMessage(Messages.text("base-teleport-warmup-task.nie-udalo-sie-wykonac-teleportacji"));
                plugin.getLogger().warning("Could not teleport " + player.getName() + ": " + exception.getMessage());
                TeleportWarmupRegistry.completePendingOperation(player, operationToken);
            }
            return;
        }

        player.sendMessage(Messages.text("base-teleport-warmup-task.ukonczono-odliczanie-rozpoczynam-transfer"));

        try {
            if (targetWorldName == null || targetWorldName.isBlank()) {
                throw new IllegalArgumentException("Missing target world name");
            }
            String snapshot = userManager.serializePlayerDataForTransfer(player, player.getLocation());
            sectorManager.transferAfterTeleportWarmup(player, targetSector, snapshot,
                    redisKey, locationToJson(targetLocation, targetWorldName), redisTtlSeconds)
                    .whenComplete((ignored, error) ->
                            TeleportWarmupRegistry.completePendingOperation(player, operationToken));
        } catch (RuntimeException exception) {
            player.sendMessage(Messages.text("base-teleport-warmup-task.nie-udalo-sie-przygotowac-transferu", exception.getMessage()));
            plugin.getLogger().warning("Could not prepare warmup transfer for " + player.getName()
                    + ": " + exception.getMessage());
            TeleportWarmupRegistry.completePendingOperation(player, operationToken);
        }
    }

    public static String locationToJson(Location loc) {
        if (loc == null || loc.getWorld() == null) throw new IllegalArgumentException("Location world is missing");
        return locationToJson(loc, loc.getWorld().getName());
    }

    private static String locationToJson(Location loc, String worldName) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", worldName);
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        return GSON.toJson(data);
    }

    private boolean isCurrentSession() {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }
}
