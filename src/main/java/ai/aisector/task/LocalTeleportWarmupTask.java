package ai.aisector.task; // Upewnij się, że pakiet jest poprawny

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class LocalTeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final SectorPlugin plugin;
    private final Location startLocation;
    private final Location targetLocation;
    private final String successMessage;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final TeleportCountdown countdown = new TeleportCountdown(5);
    private final BossBar bossBar;

    public LocalTeleportWarmupTask(Player player, Location targetLocation, String successMessage,
                                   SectorManager sectorManager, WorldBorderManager borderManager,
                                   SectorPlugin plugin) {
        this.player = player;
        this.plugin = plugin;
        this.startLocation = player.getLocation().clone();
        this.targetLocation = targetLocation;
        this.successMessage = successMessage;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
        this.bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
    }

    public void start() {
        if (plugin.isCombatTagged(player)) {
            player.sendMessage(Messages.text("combat.command-blocked"));
            return;
        }
        if (!TeleportWarmupRegistry.registerWarmup(player, this)) {
            bossBar.removeAll();
            player.sendMessage(Messages.text("local-teleport-warmup-task.poprzednia-teleportacja-jest-juz-w-trakcie-wykonywania"));
            return;
        }
        try {
            countdown.start();
            countdown.show(player, bossBar);
            bossBar.addPlayer(player);
            runTaskTimer(plugin, 1L, 1L);
        } catch (RuntimeException exception) {
            TeleportWarmupRegistry.unregister(player, this);
            bossBar.removeAll();
            countdown.clear(player);
            throw exception;
        }
    }

    @Override
    public void run() {
        if (!isCurrentSession()) {
            this.cancel();
            return;
        }

        if (!player.getWorld().equals(startLocation.getWorld())) {
            player.sendMessage(Messages.text("local-teleport-warmup-task.teleportacja-anulowana-zmieniles-swiat"));
            cancel();
            return;
        }
        if (player.getLocation().distanceSquared(startLocation) > 1.0) {
            player.sendMessage(Messages.text("local-teleport-warmup-task.teleportacja-anulowana-ruszyles-sie"));
            cancel();
            return;
        }

        if (!countdown.show(player, bossBar)) teleportPlayer();
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        try {
            super.cancel();
        } catch (IllegalStateException ignored) {
            // The task may be cancelled before the scheduler starts it.
        } finally {
            TeleportWarmupRegistry.unregister(player, this);
            if (bossBar != null) bossBar.removeAll();
            countdown.clear(player);
        }
    }

    private void teleportPlayer() {
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
        player.sendMessage(Messages.text("local-teleport-warmup-task.teleportacja"));
        try {
            player.teleportAsync(targetLocation).whenComplete((success, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                    try {
                        if (!isCurrentSession()) return;
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            player.sendMessage(Messages.text("local-teleport-warmup-task.teleportacja-nie-powiodla-sie"));
                            return;
                        }
                        player.sendMessage(successMessage);
                        Sector sector = sectorManager.getSector(targetLocation.getBlockX(), targetLocation.getBlockZ());
                        if (sector != null) borderManager.sendWorldBorder(player, sector);
                    } finally {
                        TeleportWarmupRegistry.completePendingOperation(player, operationToken);
                    }
                });
                if (!scheduled) TeleportWarmupRegistry.completePendingOperation(player, operationToken);
            });
        } catch (RuntimeException exception) {
            TeleportWarmupRegistry.completePendingOperation(player, operationToken);
            player.sendMessage(Messages.text("local-teleport-warmup-task.teleportacja-nie-powiodla-sie"));
            plugin.getLogger().warning("Could not schedule local TPA teleport for " + player.getName()
                    + ": " + exception.getMessage());
        }
    }

    private boolean isCurrentSession() {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }
}
