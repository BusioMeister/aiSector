package ai.aisector.cobblex;

import ai.aisector.SectorPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CobbleXItems {
    public static NamespacedKey key(SectorPlugin plugin) {
        return new NamespacedKey(plugin, "cobblex");
    }

    public static ItemStack createCobbleX(SectorPlugin plugin, int amount) {
        ItemStack it = new ItemStack(Material.MOSSY_COBBLESTONE, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§2§lCobbleX");
        meta.setLore(List.of(
                "§7Specjalny blok losujący nagrody",
                "§7Postaw, a zniknie i odda loot",
                "§8Konfigurowalne szanse w cobblex.yml"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isCobbleX(SectorPlugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.MOSSY_COBBLESTONE) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}
