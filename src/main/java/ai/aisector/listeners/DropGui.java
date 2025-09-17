package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.drop.DropConfig;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
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
    public static final String GUI_TITLE = "§8Drop ze Stone'a";

    private final Player player;
    private final User user;
    private final RankManager rankManager;
    private final MiningLevelManager levelManager;

    public DropGui(SectorPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.user = plugin.getUserManager().getUser(player);
        this.rankManager = plugin.getRankManager();
        // jeżeli w mainie masz inny getter, podmień na właściwy (np. getMiningLevelManager)
        this.levelManager = plugin.getSkillsManager();
    }

    public void open() {
        if (user == null) {
            player.sendMessage("§cWystąpił błąd podczas wczytywania Twoich ustawień dropu.");
            return;
        }

        // 27 slotów: ramka + 5 pozycji + cobble toggle
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Ramka
        ItemStack frame = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fmeta = frame.getItemMeta();
        fmeta.setDisplayName(" ");
        frame.setItemMeta(fmeta);
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, frame);

        // Pozycje z DropConfig: ułóż po kolei od 11
        int slot = 11;
        for (Map.Entry<Material, DropConfig.DropSpec> e : DropConfig.getAll().entrySet()) {
            if (slot == 11 || slot == 12 || slot == 13 || slot == 14 || slot == 15) {
                gui.setItem(slot, createDropItem(e.getKey(), e.getValue()));
                slot++;
            }
        }

        // Przycisk do włączenia/wyłączenia Cobblestone
        gui.setItem(17, createCobbleToggleItem());

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
        Rank playerRank = rankManager.getPlayerRank(player.getUniqueId());
        double rankBonus = levelManager.getRankBonusPercent(playerRank, material);  // w %
        double totalChance = spec.baseChancePct() + levelBonus + rankBonus;        // w %

        List<String> lore = new ArrayList<>();
        lore.add("§8§m-------------------------");
        lore.add("§f» §7Szansa na drop: §b" + pct.format(totalChance) + " §aBONUS§7(" + bonus.format(levelBonus) + ")");
        lore.add("§f» §7Bonus rangi: §a+" + pct.format(rankBonus));
        lore.add("§f» §7Wypada poniżej: §eY: " + spec.maxYLevel());
        lore.add("§f» §7Szczęście: §aWłączone");
        lore.add("§f» §7Drop: " + (isEnabled ? "§aWłączony" : "§cWyłączony"));
        lore.add("§f» §7Punkty: §e" + spec.xpValue() + " pkt.");
        lore.add("§f» §7Wykopałeś: §6" + user.getMinedBlockCount(material));
        lore.add(" ");
        lore.add("§fPosiadasz rangę: §6" + (playerRank != null ? playerRank.getName() : "Gracz"));
        lore.add("§8§m-------------------------");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCobbleToggleItem() {
        boolean enabled = user.isCobblestoneDropEnabled();
        ItemStack item = new ItemStack(Material.COBBLESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lCobblestone");

        List<String> lore = new ArrayList<>();
        lore.add("§8§m-------------------------");
        lore.add("§7Status: " + (enabled ? "§aWłączony" : "§cWyłączony"));
        lore.add("§8§m-------------------------");

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
