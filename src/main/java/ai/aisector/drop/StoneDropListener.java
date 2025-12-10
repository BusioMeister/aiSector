package ai.aisector.drop;

import ai.aisector.SectorPlugin;
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
    private final MiningLevelManager levelManager;
    public static final Map<Material, Double> baseDropChances = new HashMap<>();
    private static final int STONE_VANILLA_XP = 1;
    private final Random random = new Random();

    static {
        baseDropChances.put(Material.DIAMOND, 1.2);
        baseDropChances.put(Material.GOLD_INGOT, 1.6);
        baseDropChances.put(Material.IRON_INGOT, 2.0);
        baseDropChances.put(Material.LAPIS_LAZULI, 1.8);
        baseDropChances.put(Material.REDSTONE, 2.6);
        baseDropChances.put(Material.COAL, 2.0);
        baseDropChances.put(Material.BOOK, 1.2);
        baseDropChances.put(Material.GUNPOWDER, 1.3);
        baseDropChances.put(Material.SAND, 1.1);
        baseDropChances.put(Material.APPLE, 1.2);
        baseDropChances.put(Material.OBSIDIAN, 1.6);
        baseDropChances.put(Material.ENDER_PEARL, 0.1);
    }

    public StoneDropListener(SectorPlugin plugin, MiningLevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        String blockName = blockType.name();

        // 1) Rudy: naliczanie XP po materialu przetworzonym (zgodnym z tabelą XP w MiningLevelManager)
        if (blockName.endsWith("_ORE")) {
            User user = plugin.getUserManager().getUser(player);
            if (user == null) return;

            user.incrementMinedBlockCount(blockType, 1);

            Material dropType = getDropFromOre(blockType);
            int xpGained = levelManager.getExperienceFor(dropType);
            if (xpGained > 0) {
                levelManager.addExperience(player, dropType, 1);
                sendDropActionBar(player, dropType, 1, xpGained, true);
            }
            return;
        }

        // 2) Kopanie STONE
        if (blockType.equals(Material.STONE)) {
            User user = plugin.getUserManager().getUser(player);
            if (user == null) return;

            event.setDropItems(false);
            user.incrementMinedBlockCount(Material.STONE, 1);

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType() == Material.AIR || !handItem.getType().name().endsWith("_PICKAXE")) return;

            boolean hasSilkTouch = handItem.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
            int fortuneLevel = hasSilkTouch ? 0 : handItem.getEnchantmentLevel(Enchantment.FORTUNE);

            // Jedna pętla losująca rzadkie dropy (z bonusem LP + poziomu kopania)
            for (Map.Entry<Material, DropConfig.DropSpec> e : DropConfig.getAll().entrySet()) {
                Material material = e.getKey();
                DropConfig.DropSpec spec = e.getValue();

                // Limit wysokości z konfiguracji
                if (event.getBlock().getY() > spec.maxYLevel()) continue;

                double basePct = e.getValue().baseChancePct();
                double rankBonusPct = levelManager.getRankBonusPercent(player, material); // LP meta / fallback
                double levelBonusPct = levelManager.getDropChanceBonus(user.getMiningLevel());

                double totalPct = Math.max(0.0, Math.min(100.0, basePct + rankBonusPct + levelBonusPct));
                double roll = random.nextDouble() * 100.0;

                if (roll <= totalPct) {
                    int amount = 1 + (fortuneLevel > 0 ? random.nextInt(fortuneLevel + 1) : 0);

                    // Zużyj kilof + XP
                    damagePickaxe(handItem, player);
                    levelManager.addExperience(player, material, amount);

                    int xp = levelManager.getExperienceFor(material) * amount;

                    if (user.isDropEnabled(material)) {
                        player.getInventory().addItem(new ItemStack(material, amount));
                        sendDropActionBar(player, material, amount, xp, true);
                    } else {
                        sendDropActionBar(player, material, amount, xp, false);
                    }
                    return; // kończ po pierwszym trafieniu
                }
            }

            // 3) Domyślny drop: Stone przy Silk Touch, inaczej Cobblestone (jeśli włączony)
            if (hasSilkTouch) {
                player.getInventory().addItem(new ItemStack(Material.STONE));
                damagePickaxe(handItem, player);
                player.giveExp(STONE_VANILLA_XP);
            } else if (user.isCobblestoneDropEnabled()) {
                player.getInventory().addItem(new ItemStack(Material.COBBLESTONE));
                damagePickaxe(handItem, player);
                player.giveExp(STONE_VANILLA_XP);
            } else {
                player.giveExp(STONE_VANILLA_XP);
            }
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

    // Ujednolicone z tabelą XP: używamy GOLD_INGOT / IRON_INGOT zamiast RAW_*
    private Material getDropFromOre(Material oreType) {
        switch (oreType.name()) {
            case "DIAMOND_ORE":
            case "DEEPSLATE_DIAMOND_ORE": return Material.DIAMOND;
            case "GOLD_ORE":
            case "DEEPSLATE_GOLD_ORE": return Material.GOLD_INGOT;
            case "IRON_ORE":
            case "DEEPSLATE_IRON_ORE": return Material.IRON_INGOT;
            case "LAPIS_ORE":
            case "DEEPSLATE_LAPIS_ORE": return Material.LAPIS_LAZULI;
            case "REDSTONE_ORE":
            case "DEEPSLATE_REDSTONE_ORE": return Material.REDSTONE;
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
