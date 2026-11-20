package ai.aisector.sectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

/** Applies a client-local border; does not mutate the world's actual border. */
public final class WorldBorderManager {
    private final Map<UUID, BorderState> lastBorders = new HashMap<>();
    private final java.util.function.BooleanSupplier lockedSpawn;

    public WorldBorderManager(java.util.function.BooleanSupplier lockedSpawn) {
        this.lockedSpawn = lockedSpawn;
    }

    private record BorderState(Player player, UUID worldId, BorderView view, WorldBorder border) { }

    public void sendWorldBorder(Player player, Sector sector) {
        if (player != null) sendWorldBorder(player, sector, player.getLocation());
    }

    public void sendWorldBorder(Player player, Sector sector, Location position) {
        sendWorldBorder(player, sector, position, false);
    }

    /** The client can reset its border while joining a different backend or dimension. */
    public void refreshAfterJoin(Player player, Sector sector, Location position) {
        sendWorldBorder(player, sector, position, true);
    }

    private void sendWorldBorder(Player player, Sector sector, Location position, boolean forceRefresh) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        UUID worldId = position.getWorld().getUID();
        double showDistance = lockedSpawn.getAsBoolean() ? BorderView.SPAWN_SHOW_DISTANCE : BorderView.GAME_SHOW_DISTANCE;
        BorderView view = BorderView.at(sector, position.getX(), position.getZ(), showDistance);
        BorderState previous = lastBorders.get(uuid);
        if (view.type() == BorderType.AWAY) {
            clear(player);
            return;
        }
        if (!forceRefresh && previous != null && previous.player() == player && previous.worldId().equals(worldId)
                && previous.view().equals(view) && player.getWorldBorder() == previous.border()) return;
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(view.centerX(), view.centerZ());
        border.setSize(BorderView.SIZE);
        border.setWarningDistance(0);
        border.setWarningTime(0);
        player.setWorldBorder(border);
        lastBorders.put(uuid, new BorderState(player, worldId, view, border));
    }

    public void clear(Player player) {
        if (player == null) return;
        BorderState previous = lastBorders.get(player.getUniqueId());
        if (previous == null || previous.player() != player) return;
        lastBorders.remove(player.getUniqueId());
        if (player.isOnline() && player.getWorldBorder() == previous.border()) player.setWorldBorder(null);
    }

    public void clearAll() {
        for (BorderState state : java.util.List.copyOf(lastBorders.values())) clear(state.player());
    }
}
