package ai.aisector.sectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies a per-player border that stays within the player's current sector. */
public final class WorldBorderManager {
    private static final double BORDER_VIEW_SIZE = 200.0;
    private static final double CROSSING_BUFFER = 2.0;
    private static final double CENTER_UPDATE_DISTANCE = 8.0;
    private static final double CENTER_UPDATE_DISTANCE_SQUARED = CENTER_UPDATE_DISTANCE * CENTER_UPDATE_DISTANCE;
    private final Map<UUID, BorderState> lastBorders = new ConcurrentHashMap<>();

    private record BorderState(UUID worldId, String sectorName, double centerX, double centerZ) { }

    public void sendWorldBorder(Player player, Sector sector) {
        if (player == null || !player.isOnline() || sector == null) {
            return;
        }

        Location playerLocation = player.getLocation();
        double halfSize = BORDER_VIEW_SIZE / 2.0;
        double centerX = clamp(playerLocation.getX(), sector.getMinX() + halfSize - CROSSING_BUFFER,
                sector.getMaxX() - halfSize + CROSSING_BUFFER);
        double centerZ = clamp(playerLocation.getZ(), sector.getMinZ() + halfSize - CROSSING_BUFFER,
                sector.getMaxZ() - halfSize + CROSSING_BUFFER);

        BorderState nextState = new BorderState(player.getWorld().getUID(), sector.getName(), centerX, centerZ);
        BorderState previousState = lastBorders.get(player.getUniqueId());
        if (previousState != null && !shouldUpdateBorder(previousState.worldId(), previousState.sectorName(),
                previousState.centerX(), previousState.centerZ(), nextState.worldId(), nextState.sectorName(),
                centerX, centerZ)) {
            return;
        }

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(BORDER_VIEW_SIZE);
        border.setWarningDistance(1);
        player.setWorldBorder(border);
        lastBorders.put(player.getUniqueId(), nextState);
    }

    public void clear(Player player) {
        if (player != null) {
            lastBorders.remove(player.getUniqueId());
        }
    }

    public void clearAll() {
        lastBorders.clear();
    }

    private static double squaredDistance(double x1, double z1, double x2, double z2) {
        double deltaX = x2 - x1;
        double deltaZ = z2 - z1;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    static boolean shouldUpdateBorder(UUID previousWorldId, String previousSectorName,
                                      double previousX, double previousZ,
                                      UUID nextWorldId, String nextSectorName,
                                      double nextX, double nextZ) {
        return !previousWorldId.equals(nextWorldId)
                || !previousSectorName.equals(nextSectorName)
                || squaredDistance(previousX, previousZ, nextX, nextZ) >= CENTER_UPDATE_DISTANCE_SQUARED;
    }

    private static double clamp(double value, double minimum, double maximum) {
        // Narrow sectors cannot satisfy both border limits; center the border instead.
        if (minimum > maximum) {
            return (minimum + maximum) / 2.0;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
