package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class DropGuiListener implements Listener {

    private static final Set<Material> TOGGLEABLE = EnumSet.of(
            Material.DIAMOND,
            Material.GOLD_INGOT,
            Material.IRON_INGOT,
            Material.LAPIS_LAZULI,
            Material.REDSTONE,
            Material.COBBLESTONE
    );

    private final SectorPlugin plugin;

    public DropGuiListener(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(DropGui.GUI_TITLE)) return;
        event.setCancelled(true); // blokuje wyciąganie i wkładanie czegokolwiek

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        User user = plugin.getUserManager().getUser(player);
        if (user == null) return;

        Material mat = clicked.getType();
        if (!TOGGLEABLE.contains(mat)) return;

        if (mat == Material.COBBLESTONE) {
            user.setCobblestoneDropEnabled(!user.isCobblestoneDropEnabled());
        } else {
            boolean state = user.isDropEnabled(mat);
            user.setDropEnabled(mat, !state);
        }

        // odśwież GUI po zmianie
        new DropGui(plugin, player).open();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(DropGui.GUI_TITLE)) return;
        event.setCancelled(true); // blokuje przeciąganie itemów po GUI
    }
}
