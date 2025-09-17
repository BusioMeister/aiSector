package ai.aisector.drop;

import ai.aisector.SectorPlugin;
import ai.aisector.cobblex.CobbleXGui;
import ai.aisector.user.User;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class DropGuiListener implements Listener {

    // Materiały, które można przełączać w oknie „Stone”
    private static final Set<Material> TOGGLEABLE = EnumSet.of(
            Material.DIAMOND,
            Material.GOLD_INGOT,
            Material.IRON_INGOT,
            Material.LAPIS_LAZULI,
            Material.REDSTONE,
            Material.COBBLESTONE,

            Material.COAL,
            Material.BOOK,
            Material.GUNPOWDER,
            Material.SAND,
            Material.APPLE,
            Material.OBSIDIAN,
            Material.ENDER_PEARL


            // osobny przycisk: włącz/wyłącz cobble
    );

    private final SectorPlugin plugin;

    public DropGuiListener(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // 1) Menu główne /drop
        if (title.equals(DropMainGui.TITLE)) {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            Material type = clicked.getType();

            if (type == Material.STONE) {
                new DropGui(plugin, p).open();
            } else if (type == Material.MOSSY_COBBLESTONE) {
                CobbleXGui.open(plugin, p);

            }
            return;
        }
        if (title.equals(CobbleXGui.TITLE)) {
            event.setCancelled(true);
        }


        // 2) Ekran ustawień Stone
        if (title.equals(DropGui.GUI_TITLE)) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            User user = plugin.getUserManager().getUser(player);
            if (user == null) return;

            Material mat = clicked.getType();
            if (!TOGGLEABLE.contains(mat)) return;

            if (mat == Material.COBBLESTONE) {
                user.setCobblestoneDropEnabled(!user.isCobblestoneDropEnabled());
            } else {
                boolean current = user.isDropEnabled(mat);
                user.setDropEnabled(mat, !current);
            }

            // Odśwież widok po zmianie
            new DropGui(plugin, player).open();
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(DropMainGui.TITLE) || title.equals(DropGui.GUI_TITLE)) {
            event.setCancelled(true);
        }
    }
}
