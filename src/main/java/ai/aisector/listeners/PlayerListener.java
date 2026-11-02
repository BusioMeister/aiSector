package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.Direction;
import ai.aisector.utils.BukkitTaskScheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<UUID, UUID> pendingSectorTransfers = new ConcurrentHashMap<>();

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
        Player joiningPlayer = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isCurrentSession(joiningPlayer)) {
                plugin.getScoreboardManager().createBoard(joiningPlayer);
            }
        }, 10L);
    }



    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld().equals(to.getWorld()))) {
            return;
        }
        if (pendingSectorTransfers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        updateBorderAndBossBar(player);
        String previousSectorId = this.sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = this.sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());
        if (previousSectorId != null && !previousSectorId.equals(newSectorId)) {
            if (newSectorId == null || newSectorId.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage("§cGRANICA SEKTORA!.");
                return;
            }
            UUID transferToken = UUID.randomUUID();
            UUID playerUuid = player.getUniqueId();
            if (pendingSectorTransfers.putIfAbsent(playerUuid, transferToken) != null) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            removeBossBar(player);
            Direction moveDirection = Direction.fromLocations(from, to);
            Location adjustedLocation = moveDirection.add(to, 2);
            try {
                String snapshot = userManager.serializePlayerDataForTransfer(player, adjustedLocation);
                sectorManager.transferPlayerAsync(player, newSectorId, snapshot).whenComplete((ignored, error) -> {
                    if (error != null) {
                        pendingSectorTransfers.remove(playerUuid, transferToken);
                    } else {
                        BukkitTaskScheduler.execute(plugin, () -> Bukkit.getScheduler().runTaskLater(plugin,
                                () -> pendingSectorTransfers.remove(playerUuid, transferToken), 200L));
                    }
                });
            } catch (RuntimeException exception) {
                pendingSectorTransfers.remove(playerUuid, transferToken);
                plugin.getLogger().warning("Could not capture transfer state for " + player.getName()
                        + ": " + exception.getMessage());
                player.sendMessage("§cNie udało się przygotować transferu. Pozostajesz na obecnym sektorze.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getScoreboardManager().removeBoard(event.getPlayer());
        borderManager.clear(event.getPlayer());
        pendingSectorTransfers.remove(event.getPlayer().getUniqueId());
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

    private boolean isCurrentSession(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
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
