package ai.aisector.player;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiClickListener implements Listener {

    // Tytuł naszego GUI, którego będziemy pilnować
    private final Component guiTitle = Component.text("Informacje o Sektorach");

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Sprawdź, czy tytuł otwartego ekwipunku zgadza się z tytułem naszego GUI
        if (event.getView().title().equals(guiTitle)) {
            // Jeśli tak, anuluj zdarzenie.
            // Gracz nie będzie mógł podnieść, przenieść ani upuścić przedmiotu.
            event.setCancelled(true);
        }
    }
}