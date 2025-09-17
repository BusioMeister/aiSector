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
import java.util.*;

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
        this.levelManager = plugin.getSkillsManager(); // Zakładam, że masz getter w SectorPlugin
    }

    public void open() {
        if (user == null) {
            player.sendMessage("§cWystąpił błąd podczas wczytywania Twoich ustawień dropu.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        int slot = 11; // Zaczynamy od środka

        for (Map.Entry<Material, DropConfig.DropSpec> e : DropConfig.getAll().entrySet()) {
            gui.setItem(slot++, createDropItem(e.getKey(), e.getValue()));
        }

        // Ustawiamy ramkę
        gui.setItem(17, createCobbleToggleItem());

        ItemStack frame = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = frame.getItemMeta();
        meta.setDisplayName(" ");
        frame.setItemMeta(meta);
        for(int i = 0; i < gui.getSize(); i++){
            if(gui.getItem(i) == null) gui.setItem(i, frame);
        }

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

        double levelBonus = levelManager.getDropChanceBonus(user.getMiningLevel()); // %
        Rank playerRank = rankManager.getPlayerRank(player.getUniqueId());
        double rankBonus = levelManager.getRankBonusPercent(playerRank, material);  // %
        double totalChance = spec.baseChancePct() + levelBonus + rankBonus;         // %

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
        lore.add("§7Kliknij, aby " + (enabled ? "§cwylączyć" : "§awłączyć") + "§7.");
        lore.add("§8§m-------------------------");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }


    // Mała klasa wewnętrzna do przechowywania danych o dropie
    private static class DropInfo {
        double baseChance;
        int maxYLevel;
        int xpValue;

        DropInfo(double baseChance, int maxYLevel, int xpValue) {
            this.baseChance = baseChance;
            this.maxYLevel = maxYLevel;
            this.xpValue = xpValue;
        }

        String getDisplayName(Material mat) {
            return "§d§l" + mat.name().replace("_", " ").toUpperCase();
        }
    }
}