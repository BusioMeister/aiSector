package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DropMainGui {
    public static final String TITLE = "§8DROP";
    public static final int SIZE = 9; // 7 funkcjonalnych slotów można uzupełnić później

    public static void open(SectorPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        ItemStack stone = new ItemStack(Material.STONE);
        ItemMeta sm = stone.getItemMeta();
        sm.setDisplayName("§aUstawienia Dropu z Stone");
        stone.setItemMeta(sm);
        inv.setItem(0, stone);

        ItemStack cx = new ItemStack(Material.MOSSY_COBBLESTONE);
        ItemMeta cm = cx.getItemMeta();
        cm.setDisplayName("§aCobbleX");
        cm.setLore(java.util.List.of("§7Podgląd łupów i szans"));
        cx.setItemMeta(cm);
        inv.setItem(1, cx);

        p.openInventory(inv);
    }
}
