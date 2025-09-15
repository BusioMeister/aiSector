package ai.aisector.task; // Upewnij się, że pakiet jest poprawny

import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.CompletableFuture;

public class LocalTeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final Location targetLocation;
    private final String successMessage;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private int timeLeft = 5; // 5 sekund odliczania
    private final BossBar bossBar;

    public LocalTeleportWarmupTask(Player player, Location targetLocation, String successMessage, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.player = player;
        this.targetLocation = targetLocation;
        this.successMessage = successMessage;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
        this.bossBar = org.bukkit.Bukkit.createBossBar("§aTeleportacja za: §e" + timeLeft, BarColor.GREEN, BarStyle.SOLID);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        if (timeLeft <= 0) {
            this.cancel();
            teleportPlayer();
            return;
        }

        bossBar.setTitle("§aTeleportacja za: §e" + timeLeft);
        bossBar.setProgress((double) timeLeft / 5.0);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cNie ruszaj się! §7Teleportacja za §e" + timeLeft + "s"));
        timeLeft--;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void teleportPlayer() {
        player.sendMessage("§aTeleportacja...");
        CompletableFuture<Boolean> teleportFuture = player.teleportAsync(targetLocation);
        teleportFuture.thenAccept(success -> {
            if (success) {
                player.sendMessage(successMessage);
                borderManager.sendWorldBorder(player, sectorManager.getSector(targetLocation.getBlockX(), targetLocation.getBlockZ()));
            } else {
                player.sendMessage("§cTeleportacja nie powiodła się.");
            }
        });
    }
}