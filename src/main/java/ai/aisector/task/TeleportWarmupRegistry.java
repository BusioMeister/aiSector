package ai.aisector.task;

import ai.aisector.utils.Messages;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportWarmupRegistry {
    private record PendingOperation(UUID token) { }

    private static final ConcurrentHashMap<UUID, Object> ACTIVE = new ConcurrentHashMap<>();

    private TeleportWarmupRegistry() { }

    static boolean registerWarmup(Player player, BukkitRunnable task) {
        UUID playerUuid = player.getUniqueId();
        while (true) {
            Object current = ACTIVE.get(playerUuid);
            if (current instanceof PendingOperation) return false;
            if (current == task) return true;
            if (current == null) {
                if (ACTIVE.putIfAbsent(playerUuid, task) == null) return true;
                continue;
            }
            if (current instanceof BukkitRunnable previous && ACTIVE.replace(playerUuid, current, task)) {
                previous.cancel();
                if (player.isOnline()) player.sendMessage(Messages.text("teleport-warmup-registry.poprzednia-teleportacja-zostala-zastapiona-nowa"));
                return true;
            }
        }
    }

    static void unregister(Player player, BukkitRunnable task) {
        ACTIVE.remove(player.getUniqueId(), task);
    }

    static Object beginPendingOperation(Player player, BukkitRunnable task) {
        PendingOperation pending = new PendingOperation(UUID.randomUUID());
        return ACTIVE.replace(player.getUniqueId(), task, pending) ? pending : null;
    }

    static void completePendingOperation(Player player, Object token) {
        if (token instanceof PendingOperation pending) {
            ACTIVE.remove(player.getUniqueId(), pending);
        }
    }

    public static void clearAll() {
        for (Object operation : ACTIVE.values()) {
            if (operation instanceof BukkitRunnable task) {
                try {
                    task.cancel();
                } catch (RuntimeException ignored) {
                    // Ignore tasks already removed by Bukkit during shutdown.
                }
            }
        }
        ACTIVE.clear();
    }

    public static void cancelWarmup(Player player) {
        UUID id = player.getUniqueId();
        Object operation = ACTIVE.get(id);
        if (operation instanceof BukkitRunnable task && ACTIVE.remove(id, operation)) task.cancel();
    }
}
