package ai.aisector.drop;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DropGui {

    private final SectorPlugin plugin;

    private final Player player;
    private final User user;
    private final MiningLevelManager levelManager;

    public DropGui(SectorPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.user = plugin.getUserManager().getUser(player);
        // jeżeli w mainie jest inny getter, podmień na właściwy (np. getMiningLevelManager)
        this.levelManager = plugin.getSkillsManager();
    }

    public void open() {
        if (user == null) {
            player.sendMessage(Messages.text("drop-gui.wystapil-blad-podczas-wczytywania-twoich-ustawien-dropu"));
            return;
        }

        // Ile pozycji + 1 przycisk Cobblestone
        int count = DropConfig.getAll().size();
        int extras = 1;
        int size = ((count + extras + 8) / 9) * 9; // 9,18,27,36,45,54
        if (size < 9) size = 9;
        if (size > 54) size = 54; // bezpieczeństwo

        Inventory gui = new ai.aisector.utils.PluginMenu(ai.aisector.utils.PluginMenu.Kind.STONE, null, size,
                Messages.text("drop-gui.title")).getInventory();

        int slot = 0;
        for (Map.Entry<Material, DropConfig.DropSpec> e : DropConfig.getAll().entrySet()) {
            if (slot >= size) break; // gdyby count > size (po limicie 54)
            gui.setItem(slot++, createDropItem(e.getKey(), e.getValue()));
        }

        // Przycisk Cobblestone: wstaw w następny wolny slot lub w ostatni
        int cobbleSlot = slot < size ? slot : size - 1;
        gui.setItem(cobbleSlot, createCobbleToggleItem());

        player.openInventory(gui);
    }

    private ItemStack createDropItem(Material material, DropConfig.DropSpec spec) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        boolean isEnabled = user.isDropEnabled(material);

        meta.setDisplayName((isEnabled ? "§a" : "§c") + "§d§l" + material.name().replace("_", " ").toUpperCase());
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        DecimalFormat pct = new DecimalFormat("0.00'%'");
        DecimalFormat bonus = new DecimalFormat("+#.####");

        double levelBonus = levelManager.getDropChanceBonus(user.getMiningLevel()); // w %
        double rankBonus = levelManager.getRankBonusPercent(player, material);      // w % (LuckPerms meta / fallback)
        double totalChance = spec.baseChancePct() + levelBonus + rankBonus;         // w %

        List<String> lore = new ArrayList<>();
        lore.add("§8§m-------------------------");
        lore.add(Messages.text("drop-gui.szansa-na-drop-bonus", pct.format(totalChance), bonus.format(levelBonus)));
        lore.add(Messages.text("drop-gui.bonus-rangi", pct.format(rankBonus)));
        lore.add(Messages.text("drop-gui.wypada-ponizej-y", spec.maxYLevel()));
        lore.add(Messages.text("drop-gui.szczescie-wlaczone"));
        lore.add(Messages.text("drop-gui.drop", (isEnabled ? "§aWłączony" : "§cWyłączony")));
        lore.add(Messages.text("drop-gui.punkty-pkt", spec.xpValue()));
        lore.add(Messages.text("drop-gui.wykopales", user.getMinedBlockCount(material)));
        lore.add("§8§m-------------------------");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCobbleToggleItem() {
        boolean enabled = user.isCobblestoneDropEnabled();
        ItemStack item = new ItemStack(Material.COBBLESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.text("drop-gui.cobblestone"));

        List<String> lore = new ArrayList<>();
        lore.add("§8§m-------------------------");
        lore.add(Messages.text("drop-gui.status", (enabled ? "§aWłączony" : "§cWyłączony")));
        lore.add("§8§m-------------------------");

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
