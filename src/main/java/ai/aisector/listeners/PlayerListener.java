package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorEdge;
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
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
    private final Map<UUID, Long> lastMapEdgeAlert = new HashMap<>();
    private final java.util.Set<UUID> queuedEdgePushes = new java.util.HashSet<>();
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
        if (to == null || !from.getWorld().equals(to.getWorld())
                || (from.getX() == to.getX() && from.getZ() == to.getZ())) return;
        if (pendingSectorTransfers.containsKey(player.getUniqueId()) || userManager.isTransferPending(player)) {
            if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) event.setCancelled(true);
            return;
        }

        Sector previous = sectorManager.getSectorAt(from);
        boolean lockedSpawn = sectorManager.isLockedSpawnBackend();
        if (previous != null) {
            SectorEdge.Push push = SectorEdge.repulsion(previous, from.getX(), from.getZ(), to.getX(), to.getZ(),
                    (x, z) -> lockedSpawn ? null : sectorManager.getSector(x, z));
            if (push.active()) {
                if (!previous.isInside(to.getBlockX(), to.getBlockZ())) event.setCancelled(true);
                repelAtMapEdge(player, push);
                return;
            }
        }
        if (lockedSpawn && !sectorManager.insideLockedSpawn(to)) {
            event.setCancelled(true);
            return;
        }
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
        lastMapEdgeAlert.remove(player.getUniqueId());
        queuedEdgePushes.remove(player.getUniqueId());
        removeBossBar(player);
    }

    private void updateBorderAndBossBar(Player player, Location loc) {
        Sector sector = sectorManager.getSectorAt(loc);
        borderManager.sendWorldBorder(player, sector, loc);
        if (sector == null || sector.distanceToEdge(loc.getX(), loc.getZ()) > WARNING_DISTANCE) {
            removeBossBar(player);
            return;
        }
        SectorEdge edge = SectorEdge.nearest(sector, loc.getX(), loc.getZ());
        Sector neighbor = sectorManager.isLockedSpawnBackend() ? null
                : edge.neighbor(sector, loc.getBlockX(), loc.getBlockZ(), sectorManager::getSector);
        showBossBar(player, neighbor, Math.max(0, edge.distance(sector, loc.getX(), loc.getZ())));
    }

    private void repelAtMapEdge(Player player, SectorEdge.Push push) {
        UUID id = player.getUniqueId();
        if (queuedEdgePushes.add(id)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                queuedEdgePushes.remove(id);
                if (!isCurrentSession(player) || userManager.isTransferPending(player)
                        || pendingSectorTransfers.containsKey(id)) return;
                Vector inward = new Vector(push.x(), 0, push.z()).normalize().multiply(0.65);
                inward.setY(player.getVelocity().getY());
                player.setVelocity(inward);
            });
        }
        long now = System.nanoTime();
        Long previous = lastMapEdgeAlert.get(id);
        if (previous == null || now - previous >= 1_000_000_000L) {
            lastMapEdgeAlert.put(id, now);
            player.sendActionBar(Component.text("Jesteś na granicy mapy!", NamedTextColor.RED));
        }
    }

    private boolean isCurrentSession(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private void showBossBar(Player player, Sector neighbor, double distance) {
        BossBar bar = bossBars.get(player.getUniqueId());
        int blocks = (int) Math.ceil(distance);
        String title = neighbor == null ? "§cGranica mapy §7• §f" + blocks + " m"
                : "§eZbliżasz się do sektora §f" + neighbor.getName() + " §7• §f" + blocks + " m";
        BarColor color = neighbor == null ? BarColor.RED : BarColor.YELLOW;
        double progress = Math.clamp(1 - (double) blocks / WARNING_DISTANCE, 0, 1);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            bar.setProgress(progress);
            bossBars.put(player.getUniqueId(), bar);
            bar.addPlayer(player);
            return;
        }
        if (!bar.getTitle().equals(title)) bar.setTitle(title);
        if (bar.getColor() != color) bar.setColor(color);
        if (bar.getProgress() != progress) bar.setProgress(progress);
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }
}
