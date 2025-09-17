package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.drop.DropConfig;
import ai.aisector.ranks.Rank;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.user.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class StoneDropListener implements Listener {

    private final SectorPlugin plugin;
    private final MiningLevelManager levelManager; // <-- DODAJEMY POLE
    public static final Map<Material, Double> baseDropChances = new HashMap<>();
    private static final int STONE_VANILLA_XP = 1;
    private final Random random = new Random();

    static {
        baseDropChances.put(Material.DIAMOND, 1.2);
        baseDropChances.put(Material.GOLD_INGOT, 1.6);
        baseDropChances.put(Material.IRON_INGOT, 2.0);
        baseDropChances.put(Material.LAPIS_LAZULI, 1.8);
        baseDropChances.put(Material.REDSTONE, 2.6);
    }
    public StoneDropListener(SectorPlugin plugin,MiningLevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager; // <-- INICJALIZUJEMY

    }

    // W pliku StoneDropListener.java

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        String blockName = blockType.name();

        if (blockName.endsWith("_ORE")) {
            User user = plugin.getUserManager().getUser(player);
            if (user == null) return;
            // Zliczamy wykopany blok
            user.incrementMinedBlockCount(blockType, 1);
            // Nadajemy XP za rudę
            Material dropType = getDropFromOre(blockType);
            int xpGained = levelManager.getExperienceFor(dropType);
            if (xpGained > 0) {
                levelManager.addExperience(player, dropType, 1); // Na razie za 1 sztukę
                sendDropActionBar(player, dropType, 1, xpGained, true);
            }
            return; // Zakończ, aby nie procesować dalej jako kopanie stone'a
        }
        // 2. Obsługa kopania STONE'a
        if (blockType.equals(Material.STONE)) {
            User user = plugin.getUserManager().getUser(player);
            if (user == null) return;
            event.setDropItems(false);
            user.incrementMinedBlockCount(Material.STONE, 1);
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType() == Material.AIR || !handItem.getType().name().endsWith("_PICKAXE")) return;
            // 1) Najpierw enchanty
            boolean hasSilkTouch = handItem.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
            int fortuneLevel = hasSilkTouch ? 0 : handItem.getEnchantmentLevel(Enchantment.FORTUNE);
            Rank playerRank = plugin.getRankManager().getPlayerRank(player.getUniqueId());
            // 3) Jedna pętla losująca rzadkie dropy
            for (Map.Entry<Material, DropConfig.DropSpec> e : DropConfig.getAll().entrySet()) {
                Material material = e.getKey();
                double basePct = e.getValue().baseChancePct();
                double rankBonusPct = levelManager.getRankBonusPercent(playerRank, material);
                double levelBonusPct = levelManager.getDropChanceBonus(user.getMiningLevel());
                double totalPct = Math.max(0.0, Math.min(100.0, basePct + rankBonusPct + levelBonusPct));
                double roll = random.nextDouble() * 100.0;
                // opcjonalnie: limit wysokości zgodny z GUI
                ai.aisector.drop.DropConfig.DropSpec spec = e.getValue();
                if (event.getBlock().getY() > spec.maxYLevel()) {
                    continue;
                }


                if (roll <= totalPct) {
                    int amount = 1 + (fortuneLevel > 0 ? random.nextInt(fortuneLevel + 1) : 0);

                    // zawsze zużyj kilof i nadaj XP za trafienie
                    damagePickaxe(handItem, player);
                    levelManager.addExperience(player, material, amount);

                    int xp = levelManager.getExperienceFor(material) * amount;

                    if (user.isDropEnabled(material)) {
                        // drop włączony -> dodaj item
                        player.getInventory().addItem(new ItemStack(material, amount));
                        sendDropActionBar(player, material, amount, xp, true);
                    } else {
                        // drop wyłączony -> bez itemu, ale XP i komunikat
                        sendDropActionBar(player, material, amount, xp, false);
                    }
                    return; // kończ po pierwszym trafieniu
                }

            }
            // 4) Domyślny drop: Stone przy Silk Touch, inaczej Cobblestone (jeśli włączony)
            if (hasSilkTouch) {
                player.getInventory().addItem(new ItemStack(Material.STONE));
                damagePickaxe(handItem, player);
                player.giveExp(STONE_VANILLA_XP);
            } else if (user.isCobblestoneDropEnabled()) {
                player.getInventory().addItem(new ItemStack(Material.COBBLESTONE));
                damagePickaxe(handItem, player);
                player.giveExp(STONE_VANILLA_XP);
            }else{
                player.giveExp(STONE_VANILLA_XP);
            }
            return;
        }
    }
    private void sendDropActionBar(Player player, Material material, int amount, int xpGained, boolean dropEnabled) {
        String materialName = material.name().replace("_", " ").toLowerCase();

        Component message = Component.text("Udało Ci się wydobyć: ", NamedTextColor.GRAY)
                .append(Component.text(materialName.substring(0, 1).toUpperCase() + materialName.substring(1), NamedTextColor.AQUA))
                .append(Component.text(" (" + amount + " szt) ", NamedTextColor.GRAY))
                .append(Component.text("+" + xpGained, NamedTextColor.GOLD));

        if (!dropEnabled) {
            message = message.append(Component.text(" (Off)", NamedTextColor.RED));
        }

        player.sendActionBar(message);
    }


    private Material getDropFromOre(Material oreType) {
        switch (oreType.name()) {
            case "DIAMOND_ORE": case "DEEPSLATE_DIAMOND_ORE": return Material.DIAMOND;
            case "GOLD_ORE": case "DEEPSLATE_GOLD_ORE": return Material.RAW_GOLD;
            case "IRON_ORE": case "DEEPSLATE_IRON_ORE": return Material.RAW_IRON;
            case "LAPIS_ORE": case "DEEPSLATE_LAPIS_ORE": return Material.LAPIS_LAZULI;
            case "REDSTONE_ORE": case "DEEPSLATE_REDSTONE_ORE": return Material.REDSTONE;
            default: return Material.AIR;
        }
    }


    private void damagePickaxe(ItemStack pickaxe, Player player) {
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int unbreakingLevel = pickaxe.getEnchantmentLevel(Enchantment.UNBREAKING);

            if (random.nextDouble() < (1.0 / (unbreakingLevel + 1))) {
                damageable.setDamage(damageable.getDamage() + 1);
                pickaxe.setItemMeta(meta);

                if (damageable.getDamage() >= pickaxe.getType().getMaxDurability()) {
                    player.getInventory().setItemInMainHand(null);
                    player.playSound(player.getLocation(), "entity.item.break", 1.0f, 1.0f);
                }
            }
        }
    }
}