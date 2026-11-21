package ai.aisector.drop;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DropMainGui {
    public static final int SIZE = 9; // 7 funkcjonalnych slotów można uzupełnić później

    public static void open(SectorPlugin plugin, Player p) {
        Inventory inv = new ai.aisector.utils.PluginMenu(ai.aisector.utils.PluginMenu.Kind.DROP, null, SIZE,
                Messages.text("drop-main-gui.title")).getInventory();

        ItemStack stone = new ItemStack(Material.STONE);
        ItemMeta sm = stone.getItemMeta();
        sm.setDisplayName(Messages.text("drop-main-gui.ustawienia-dropu-z-stone"));
        stone.setItemMeta(sm);
        inv.setItem(0, stone);

        ItemStack cx = new ItemStack(Material.MOSSY_COBBLESTONE);
        ItemMeta cm = cx.getItemMeta();
        cm.setDisplayName(Messages.text("drop-main-gui.cobblex"));
        cm.setLore(java.util.List.of(Messages.text("drop-main-gui.podglad-lupow-i-szans")));
        cx.setItemMeta(cm);
        inv.setItem(1, cx);

        p.openInventory(inv);
    }
}
