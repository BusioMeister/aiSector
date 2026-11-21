package ai.aisector.cobblex;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CobbleXGui {
    public static void open(SectorPlugin plugin, Player p) {
        CobbleXManager mgr = plugin.getCobbleXManager(); // patrz niżej – pole w mainie
        int size = ((mgr.getTable().size() - 1) / 9 + 1) * 9;
        Inventory inv = new ai.aisector.utils.PluginMenu(ai.aisector.utils.PluginMenu.Kind.COBBLEX, null,
                Math.max(27, size), Messages.text("cobble-x-gui.title")).getInventory();
        int i = 0;
        for (CobbleXManager.Loot l : mgr.getTable()) {
            Material m = l.mat;
            ItemStack is = new ItemStack(m);
            ItemMeta im = is.getItemMeta();
            im.setDisplayName("§6" + m);
            im.setLore(java.util.List.of(
                    Messages.text("cobble-x-gui.szansa", String.format(java.util.Locale.US, "%.2f", l.chancePct)),
                    Messages.text("cobble-x-gui.ilosc-x", l.min, l.max)
            ));
            is.setItemMeta(im);
            inv.setItem(i++, is);
        }
        p.openInventory(inv);
    }
}
