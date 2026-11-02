package ai.aisector.utils; // lub inny pakiet na klasy pomocnicze

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HotbarSlotSync {
    private static final ConcurrentMap<UUID, Object> ACTIVE_SYNC = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public HotbarSlotSync(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Agresywnie wymusza ustawienie aktywnego slota hotbara,
     * aby przeciwdziałać desynchronizacji klienta po teleporcie.
     * @param p Gracz
     * @param desiredSlot Docelowy slot (0-8)
     */
    public void ensureSelectedSlot(Player p, int desiredSlot) {
        final int safe = Math.max(0, Math.min(8, desiredSlot));
        final int alt = (safe + 1) % 9; // Slot obok, używany do "odświeżenia"
        final UUID playerUuid = p.getUniqueId();
        final Object syncToken = new Object();
        ACTIVE_SYNC.put(playerUuid, syncToken);
        final Location initialLocation = p.getLocation().clone();
        final int originalSlot = p.getInventory().getHeldItemSlot();
        final AtomicBoolean cancelled = new AtomicBoolean();

        // Krok 1: Szybkie "pstryknięcie" na sąsiedni slot, by zmusić klienta do synchronizacji
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled.get() || ACTIVE_SYNC.get(playerUuid) != syncToken) return;
            if (!isSameSessionAndPlace(p, playerUuid, initialLocation)
                    || p.getInventory().getHeldItemSlot() != originalSlot) {
                cancelled.set(true);
                ACTIVE_SYNC.remove(playerUuid, syncToken);
                return;
            }
            try {
                p.getInventory().setHeldItemSlot(alt);
            } catch (IllegalArgumentException ignored) {}
            p.updateInventory(); // Wymuś aktualizację
        }, 1L);

        // Krok 2: Powrót na właściwy slot
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled.get() || ACTIVE_SYNC.get(playerUuid) != syncToken) return;
            if (!isSameSessionAndPlace(p, playerUuid, initialLocation)
                    || p.getInventory().getHeldItemSlot() != alt) {
                cancelled.set(true);
                ACTIVE_SYNC.remove(playerUuid, syncToken);
                return;
            }
            try {
                p.getInventory().setHeldItemSlot(safe);
            } catch (IllegalArgumentException ignored) {
                p.getInventory().setHeldItemSlot(0);
            }
            p.updateInventory(); // Wymuś aktualizację
        }, 2L);

        // Krok 3: Ostateczne wymuszenie po pełnej stabilizacji klienta
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled.get() || ACTIVE_SYNC.get(playerUuid) != syncToken) return;
            if (isSameSessionAndPlace(p, playerUuid, initialLocation)
                    && p.getInventory().getHeldItemSlot() == safe) {
                p.updateInventory(); // Wymuś aktualizację
            }
            ACTIVE_SYNC.remove(playerUuid, syncToken);
        }, 3L);
    }

    private boolean isSameSessionAndPlace(Player player, UUID uuid, Location location) {
        return player.isOnline() && Bukkit.getPlayer(uuid) == player
                && player.getWorld().getUID().equals(location.getWorld().getUID())
                && player.getLocation().distanceSquared(location) <= 0.01;
    }
}
