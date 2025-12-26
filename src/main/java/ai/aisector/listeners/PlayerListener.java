package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.Direction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class PlayerListener implements Listener {
    private final SectorPlugin plugin;

    private final SectorManager sectorManager;

    private final WorldBorderManager borderManager;

    private final UserManager userManager;

    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private final double WARNING_DISTANCE = 20.0D;

    public PlayerListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateBorderAndBossBar(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getScoreboardManager().createBoard(event.getPlayer());
            }
        }, 10L);
    }



    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        updateBorderAndBossBar(player);
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()))
            return;
        String previousSectorId = this.sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = this.sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());
        if (previousSectorId != null && !previousSectorId.equals(newSectorId)) {
            if (newSectorId == null || newSectorId.isEmpty()) {
                player.teleport(from);
                player.sendMessage("§cGRANICA SEKTORA!.");
                return;
            }
            removeBossBar(player);
            String finalNewSectorId = newSectorId;
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                if (!player.isOnline())
                    return;
                Location finalLocation = player.getLocation();
                Direction moveDirection = Direction.fromLocations(from, finalLocation);
                Location adjustedLocation = moveDirection.add(finalLocation, 2);
                this.userManager.savePlayerDataForTransfer(player, adjustedLocation);
                this.sectorManager.transferPlayer(player.getUniqueId(), finalNewSectorId);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getScoreboardManager().removeBoard(event.getPlayer());
        removeBossBar(event.getPlayer());
    }

    private void updateBorderAndBossBar(Player player) {
        Location loc = player.getLocation();
        Sector currentSector = this.sectorManager.getSector(loc.getBlockX(), loc.getBlockZ());
        if (currentSector != null) {
            this.borderManager.sendWorldBorder(player, currentSector);
            double distance = this.sectorManager.distanceToClosestBorder(loc);
            if (distance <= 20.0D) {
                showBossBar(player, distance);
            } else {
                removeBossBar(player);
            }
        } else {
            removeBossBar(player);
        }
    }

    private void showBossBar(Player player, double distance) {
        BossBar bar = this.bossBars.get(player.getUniqueId());
        String title = "§cGranica sektora za " + (int)distance + "§c kratek" ;
        double progress = Math.max(0.0D, Math.min(1.0D, distance / 20.0D));
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID, new org.bukkit.boss.BarFlag[0]);
            this.bossBars.put(player.getUniqueId(), bar);
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setProgress(progress);
        bar.setVisible(true);
    }

    private void removeBossBar(Player player) {
        BossBar bar = this.bossBars.remove(player.getUniqueId());
        if (bar != null)
            bar.removeAll();
    }
}