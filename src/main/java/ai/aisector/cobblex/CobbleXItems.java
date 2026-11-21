package ai.aisector.cobblex;

import ai.aisector.utils.Messages;

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
        meta.setDisplayName(Messages.text("cobble-x-items.cobblex"));
        meta.setLore(List.of(
                Messages.text("cobble-x-items.specjalny-blok-losujacy-nagrody"),
                Messages.text("cobble-x-items.postaw-a-zniknie-i-odda-loot"),
                Messages.text("cobble-x-items.konfigurowalne-szanse-w-cobblex-yml")
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
