package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorArrival;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {
    private static final int WARNING_DISTANCE = 20;
    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final UserManager userManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, UUID> pendingSectorTransfers = new ConcurrentHashMap<>();

    public PlayerListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updateBorderAndBossBar(player, player.getLocation());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isCurrentSession(player)) plugin.getScoreboardManager().createBoard(player);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();
        if (sectorManager.isLockedSpawnBackend() && to != null && !sectorManager.insideLockedSpawn(to)) {
            event.setCancelled(true);
            return;
        }
        if (to == null || !from.getWorld().equals(to.getWorld())
                || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) return;
        if (pendingSectorTransfers.containsKey(player.getUniqueId()) || userManager.isTransferPending(player)) {
            event.setCancelled(true);
            return;
        }

        Sector previous = sectorManager.getSectorAt(from);
        if (previous == null || previous.isInside(to.getBlockX(), to.getBlockZ())) return;
        Sector destination = sectorManager.getSectorAt(to);
        if (destination == null) {
            event.setCancelled(true);
            return;
        }
        UUID transferToken = UUID.randomUUID();
        UUID playerUuid = player.getUniqueId();
        if (pendingSectorTransfers.putIfAbsent(playerUuid, transferToken) != null) {
            event.setCancelled(true);
            return;
        }

        Location arrival = to.clone();
        arrival.setX(SectorArrival.inside(from.getX(), to.getX(), destination.getMinX(), destination.getMaxX()));
        arrival.setZ(SectorArrival.inside(from.getZ(), to.getZ(), destination.getMinZ(), destination.getMaxZ()));
        try {
            String snapshot = userManager.serializePlayerDataForTransfer(player, arrival);
            sectorManager.transferAcrossSeam(player, destination, arrival, snapshot, event).whenComplete((ignored, error) -> {
                if (error != null) BukkitTaskScheduler.execute(plugin, () -> {
                    if (!pendingSectorTransfers.remove(playerUuid, transferToken) || !isCurrentSession(player)
                            || userManager.isTransferPending(player)) return;
                    // Definitive failure before publication: restore the source position.
                    if (!previous.isInside(player.getLocation().getBlockX(), player.getLocation().getBlockZ()))
                        player.teleportAsync(from);
                });
                else BukkitTaskScheduler.execute(plugin, () -> Bukkit.getScheduler().runTaskLater(plugin,
                        () -> pendingSectorTransfers.remove(playerUuid, transferToken), 200L));
            });
        } catch (RuntimeException exception) {
            event.setCancelled(true);
            pendingSectorTransfers.remove(playerUuid, transferToken);
            plugin.getLogger().warning("Could not capture transfer state for " + player.getName()
                    + ": " + exception.getMessage());
            player.sendMessage("§cNie udało się przygotować transferu. Pozostajesz na obecnym sektorze.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBorderMove(PlayerMoveEvent event) {
        if (pendingSectorTransfers.containsKey(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ()
                || !from.getWorld().equals(to.getWorld()))) updateBorderAndBossBar(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBorderTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        BukkitTaskScheduler.execute(plugin, () -> {
            if (isCurrentSession(player)) updateBorderAndBossBar(player, player.getLocation());
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getScoreboardManager().removeBoard(player);
        borderManager.clear(player);
        pendingSectorTransfers.remove(player.getUniqueId());
        removeBossBar(player);
    }

    private void updateBorderAndBossBar(Player player, Location loc) {
        Sector sector = sectorManager.getSectorAt(loc);
        borderManager.sendWorldBorder(player, sector, loc);
        double distance = sector == null ? Double.POSITIVE_INFINITY : sector.distanceToEdge(loc.getX(), loc.getZ());
        if (distance <= WARNING_DISTANCE) showBossBar(player, (int) Math.max(0, distance));
        else removeBossBar(player);
    }

    private boolean isCurrentSession(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private void showBossBar(Player player, int distance) {
        BossBar bar = bossBars.get(player.getUniqueId());
        String title = "§cGranica sektora za " + distance + "§c kratek";
        double progress = (double) distance / WARNING_DISTANCE;
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
            bar.setProgress(progress);
            bossBars.put(player.getUniqueId(), bar);
            bar.addPlayer(player);
            return;
        }
        if (!bar.getTitle().equals(title)) bar.setTitle(title);
        if (bar.getProgress() != progress) bar.setProgress(progress);
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }
}
