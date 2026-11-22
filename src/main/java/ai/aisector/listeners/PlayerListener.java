package ai.aisector.listeners;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorBoatBoundary;
import ai.aisector.sectors.SectorEdge;
import ai.aisector.sectors.SectorArrival;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.SeamCrossingCooldown;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> activeSeamPushes = new HashMap<>();
    private final Set<UUID> boatRecoveries = new HashSet<>();
    private final Map<UUID, UUID> pendingSectorTransfers = new ConcurrentHashMap<>();
    private final SeamCrossingCooldown seamCooldown = new SeamCrossingCooldown();

    public PlayerListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void suppressJoinBroadcast(PlayerJoinEvent event) {
        event.joinMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void suppressQuitBroadcast(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        seamCooldown.arrived(player.getUniqueId());
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
        if (boatRecoveries.contains(player.getUniqueId())) {
            if (!sectorManager.owns(to)) event.setCancelled(true);
            return;
        }
        if (player.getVehicle() instanceof Boat boat && stopBoatCrossing(boat, from, to)) {
            event.setCancelled(true);
            return;
        }
        if (userManager.isTransferPending(player)) {
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
        if (userManager.isArriving(player)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.isCombatTagged(player)) {
            event.setCancelled(true);
            queueBoundaryPush(player, SectorEdge.backInside(previous, to.getBlockX(), to.getBlockZ()), previous);
            return;
        }
        UUID playerUuid = player.getUniqueId();
        var cooldown = seamCooldown.crossingState(playerUuid);
        if (cooldown != SeamCrossingCooldown.Crossing.ALLOWED) {
            event.setCancelled(true);
            queueBoundaryPush(player, SectorEdge.backInside(previous, to.getBlockX(), to.getBlockZ()), previous);
            if (cooldown == SeamCrossingCooldown.Crossing.BLOCKED_NOTIFY) {
                player.sendActionBar(Component.text(Messages.text("player-listener.odczekaj-2-sekundy-przed-kolejnym-przejsciem"), NamedTextColor.YELLOW));
            }
            return;
        }
        UUID transferToken = UUID.randomUUID();
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
            player.sendMessage(Messages.text("player-listener.nie-udalo-sie-przygotowac-transferu-pozostajesz-na"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        if (nearBoatBoundary(boat.getLocation())) {
            event.setCancelled(true);
            if (event.getPlayer() != null) event.getPlayer().sendActionBar(
                    Component.text(Messages.text("player-listener.nie-mozesz-postawic-lodki-przy-granicy-sektora"), NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || !(event.getEntered() instanceof Player player)) return;
        if (nearBoatBoundary(boat.getLocation()) || nearBoatBoundary(player.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(Messages.text("player-listener.nie-mozesz-wsiasc-do-lodki-przy-granicy"), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBoatMove(VehicleMoveEvent event) {
        if (event.getVehicle() instanceof Boat boat) stopBoatCrossing(boat, event.getFrom(), event.getTo());
    }

    private boolean nearBoatBoundary(Location position) {
        Sector local = sectorManager.localSectorAtWorld(position);
        return local != null && SectorBoatBoundary.blocksInteraction(local, position.getX(), position.getZ());
    }

    private boolean stopBoatCrossing(Boat boat, Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        Sector local = sectorManager.localSectorAtWorld(from);
        if (local == null) return false;
        SectorEdge.Push inward = SectorBoatBoundary.crossing(local, to.getX(), to.getZ());
        if (!inward.active()) return false;
        // The player and vehicle events can report the same movement. Never process its drops twice.
        if (!boat.isValid()) return true;
        var riders = boat.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast).toList();
        if (riders.isEmpty()) return false;
        for (Player rider : riders) boatRecoveries.add(rider.getUniqueId());

        Location safeSide = from.clone();
        safeSide.setX(Math.clamp(from.getX(), local.getMinX() + .35, local.getMaxX() + .65));
        safeSide.setZ(Math.clamp(from.getZ(), local.getMinZ() + .35, local.getMaxZ() + .65));
        if (boat instanceof ChestBoat chest) {
            var inventory = chest.getInventory();
            for (var item : inventory.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    from.getWorld().dropItem(safeSide, item.clone())
                            .setVelocity(new Vector(inward.x() * .08, .15, inward.z() * .08));
                }
            }
            inventory.clear();
        }
        boat.eject();
        boat.remove();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player rider : riders) {
                UUID id = rider.getUniqueId();
                boatRecoveries.remove(id);
                if (!isCurrentSession(rider) || !rider.getWorld().equals(from.getWorld())
                        || pendingSectorTransfers.containsKey(id) || userManager.isTransferPending(rider)) continue;
                seamCooldown.arrived(id);
                Location at = rider.getLocation();
                // VehicleMoveEvent is not cancellable. Correct only riders already reported outside.
                if (!local.isInside(at.getBlockX(), at.getBlockZ())) {
                    Location returnTo = safeSide.clone();
                    returnTo.setY(Math.max(at.getY(), safeSide.getY()));
                    returnTo.setYaw(at.getYaw());
                    returnTo.setPitch(at.getPitch());
                    if (!rider.teleport(returnTo, PlayerTeleportEvent.TeleportCause.PLUGIN)) continue;
                }
                rider.sendActionBar(Component.text(Messages.text("player-listener.lodka-rozbila-sie-na-granicy-sektora"), NamedTextColor.RED));
                pushInsideSector(rider, local, inward, true);
            }
        });
        return true;
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
        boatRecoveries.remove(player.getUniqueId());
        seamCooldown.clear(player.getUniqueId());
        lastMapEdgeAlert.remove(player.getUniqueId());
        queuedEdgePushes.remove(player.getUniqueId());
        stopSeamPush(player.getUniqueId());
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
        queueBoundaryPush(player, push, null);
        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastMapEdgeAlert.get(id);
        if (previous == null || now - previous >= 1_000_000_000L) {
            lastMapEdgeAlert.put(id, now);
            player.sendActionBar(Component.text(Messages.text("player-listener.jestes-na-granicy-mapy"), NamedTextColor.RED));
        }
    }

    private void queueBoundaryPush(Player player, SectorEdge.Push push, Sector cooldownSector) {
        UUID id = player.getUniqueId();
        if (queuedEdgePushes.add(id)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                queuedEdgePushes.remove(id);
                if (!isCurrentSession(player) || userManager.isTransferPending(player)
                        || pendingSectorTransfers.containsKey(id)) return;
                if (cooldownSector != null) {
                    pushInsideSector(player, cooldownSector, push, false);
                    return;
                }
                Vector inward = new Vector(push.x(), 0, push.z()).normalize().multiply(0.65);
                inward.setY(player.getVelocity().getY());
                player.setVelocity(inward);
            });
        }
    }

    private void pushInsideSector(Player player, Sector sector, SectorEdge.Push push, boolean allowWater) {
        UUID id = player.getUniqueId();
        if (activeSeamPushes.containsKey(id) || player.isInsideVehicle()) return;
        Location start = player.getLocation();
        if (sectorManager.getSectorAt(start) != sector) return;
        double x = SectorEdge.inset(start.getX(), push.x(), sector.getMinX(), sector.getMaxX());
        double z = SectorEdge.inset(start.getZ(), push.z(), sector.getMinZ(), sector.getMaxZ());
        boolean needsFloor = !allowWater && player.isOnGround() && !player.isFlying();
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;
            @Override
            public void run() {
                if (!isCurrentSession(player) || pendingSectorTransfers.containsKey(id)
                        || userManager.isTransferPending(player) || player.isInsideVehicle()) {
                    stopSeamPush(id);
                    return;
                }
                Location at = player.getLocation();
                if (at.getWorld() != start.getWorld() || sectorManager.getSectorAt(at) != sector
                        || at.distanceSquared(start) > 100) {
                    stopSeamPush(id);
                    return;
                }
                double dx = Math.max(0, (x - at.getX()) * push.x()) * push.x();
                double dz = Math.max(0, (z - at.getZ()) * push.z()) * push.z();
                double remaining = Math.hypot(dx, dz);
                double y = player.getVelocity().getY();
                if (++ticks > 24 || remaining <= .22) {
                    player.setVelocity(new Vector(0, y, 0));
                    stopSeamPush(id);
                    return;
                }
                // Accelerate, then ease off near the target. Every impulse uses collision-checked
                // space ahead; the client walks the intervening positions, without any teleport.
                double speed = Math.min(Math.min(.50, .22 + ticks * .07), remaining * .65);
                double vx = dx / remaining * speed;
                double vz = dz / remaining * speed;
                if (!safePushPosition(at.clone().add(vx * .75, 0, vz * .75), needsFloor, allowWater)
                        || !safePushPosition(at.clone().add(vx * 1.5, 0, vz * 1.5), needsFloor, allowWater)) {
                    player.setVelocity(new Vector(0, y, 0));
                    stopSeamPush(id);
                    return;
                }
                player.setVelocity(new Vector(vx, y, vz));
            }
        };
        activeSeamPushes.put(id, task.runTaskTimer(plugin, 0L, 1L));
    }

    private void stopSeamPush(UUID id) {
        var task = activeSeamPushes.remove(id);
        if (task != null) task.cancel();
    }

    private boolean safePushPosition(Location point, boolean needsFloor, boolean allowWater) {
        var world = point.getWorld();
        int minX = (int) Math.floor(point.getX() - .3), maxX = (int) Math.floor(point.getX() + .3);
        int minZ = (int) Math.floor(point.getZ() - .3), maxZ = (int) Math.floor(point.getZ() + .3);
        int minY = point.getBlockY(), maxY = (int) Math.floor(point.getY() + 1.8);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = minY; y <= maxY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (allowWater && block.getType() == Material.WATER) continue;
                    if (!block.isPassable() || block.isLiquid()) return false;
                }
            }
        }
        return !needsFloor || world.getBlockAt(point.getBlockX(), minY - 1, point.getBlockZ()).getType().isSolid();
    }

    private boolean isCurrentSession(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private void showBossBar(Player player, Sector neighbor, double distance) {
        BossBar bar = bossBars.get(player.getUniqueId());
        int blocks = (int) Math.ceil(distance);
        String title = neighbor == null ? Messages.text("player-listener.granica-mapy-m", blocks)
                : Messages.text("player-listener.zblizasz-sie-do-sektora-m", neighbor.getName(), blocks);
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
