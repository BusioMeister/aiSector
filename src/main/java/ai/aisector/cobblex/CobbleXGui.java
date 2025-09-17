package ai.aisector.cobblex;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CobbleXGui {
    public static final String TITLE = "§2DROP Z CobbleX";
    public static void open(SectorPlugin plugin, Player p) {
        CobbleXManager mgr = plugin.getCobbleXManager(); // patrz niżej – pole w mainie
        int size = ((mgr.getTable().size() - 1) / 9 + 1) * 9;
        Inventory inv = Bukkit.createInventory(null, Math.max(27, size), TITLE);
        int i = 0;
        for (CobbleXManager.Loot l : mgr.getTable()) {
            Material m = l.mat;
            ItemStack is = new ItemStack(m);
            ItemMeta im = is.getItemMeta();
            im.setDisplayName("§6" + m);
            im.setLore(java.util.List.of(
                    "§7Szansa: §e" + String.format(java.util.Locale.US, "%.2f", l.chancePct) + " %",
                    "§7Ilość: §ex" + l.min + "–" + l.max
            ));
            is.setItemMeta(im);
            inv.setItem(i++, is);
        }
        p.openInventory(inv);
    }
}
