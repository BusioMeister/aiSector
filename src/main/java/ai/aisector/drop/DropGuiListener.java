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
import ai.aisector.utils.PluginMenu;
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

        boolean main = PluginMenu.is(top, PluginMenu.Kind.DROP);
        boolean stone = PluginMenu.is(top, PluginMenu.Kind.STONE);
        boolean cobble = PluginMenu.is(top, PluginMenu.Kind.COBBLEX);
        if (!main && !stone && !cobble) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top || cobble) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // 1) Menu główne /drop
        if (main) {
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


        // 2) Ekran ustawień Stone
        if (stone) {
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
        Inventory top = event.getView().getTopInventory();
        if (PluginMenu.is(top, PluginMenu.Kind.DROP) || PluginMenu.is(top, PluginMenu.Kind.STONE)
                || PluginMenu.is(top, PluginMenu.Kind.COBBLEX)) {
            event.setCancelled(true);
        }
    }
}
