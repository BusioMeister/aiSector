package ai.aisector.utils; // lub inny pakiet na klasy pomocnicze

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HotbarSlotSync {
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

        // Krok 1: Szybkie "pstryknięcie" na sąsiedni slot, by zmusić klienta do synchronizacji
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            try {
                if (p.getInventory().getHeldItemSlot() != alt) {
                    p.getInventory().setHeldItemSlot(alt);
                }
            } catch (IllegalArgumentException ignored) {}
            p.updateInventory(); // Wymuś aktualizację
        }, 1L);

        // Krok 2: Powrót na właściwy slot
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            try {
                p.getInventory().setHeldItemSlot(safe);
            } catch (IllegalArgumentException ignored) {
                p.getInventory().setHeldItemSlot(0);
            }
            p.updateInventory(); // Wymuś aktualizację
        }, 2L);

        // Krok 3: Ostateczne wymuszenie po pełnej stabilizacji klienta
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            try {
                if (p.getInventory().getHeldItemSlot() != safe) {
                    p.getInventory().setHeldItemSlot(safe);
                }
            } catch (IllegalArgumentException ignored) {
                p.getInventory().setHeldItemSlot(0);
            }
            p.updateInventory(); // Wymuś aktualizację
        }, 3L);
    }
}