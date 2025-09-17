package ai.aisector.generators;

import ai.aisector.SectorPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class GeneratorItems {

    public static NamespacedKey genKey(SectorPlugin plugin) {
        return new NamespacedKey(plugin, "generator_type");
    }

    public static void registerRecipes(SectorPlugin plugin) {
        // Stoniarka
        ItemStack stoneGen = createGeneratorItem(plugin, Material.STONE, "§d§lStoniarka", GeneratorType.STONE);
        ShapedRecipe stoneRecipe = new ShapedRecipe(new NamespacedKey(plugin, "stone_generator"), stoneGen);
        stoneRecipe.shape("RRR","RSR","RRR");
        stoneRecipe.setIngredient('R', Material.REDSTONE);
        stoneRecipe.setIngredient('S', Material.STONE);
        plugin.getServer().addRecipe(stoneRecipe);

        // Obsydianarka
        ItemStack obsGen = createGeneratorItem(plugin, Material.OBSIDIAN, "§5§lObsydianarka", GeneratorType.OBSIDIAN);
        ShapedRecipe obsRecipe = new ShapedRecipe(new NamespacedKey(plugin, "obsidian_generator"), obsGen);
        obsRecipe.shape("RRR","ROR","RRR");
        obsRecipe.setIngredient('R', Material.REDSTONE);
        obsRecipe.setIngredient('O', Material.OBSIDIAN);
        plugin.getServer().addRecipe(obsRecipe);
    }

    public static ItemStack createGeneratorItem(SectorPlugin plugin, Material base, String name, GeneratorType type) {
        ItemStack it = new ItemStack(base);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(genKey(plugin), PersistentDataType.STRING, type.name());
        it.setItemMeta(meta);
        return it;
    }
    public static org.bukkit.inventory.ItemStack createItemForType(ai.aisector.SectorPlugin plugin,
                                                                   ai.aisector.generators.GeneratorType type) {
        if (type == ai.aisector.generators.GeneratorType.STONE) {
            return createGeneratorItem(plugin, org.bukkit.Material.STONE, "§d§lStoniarka",
                    ai.aisector.generators.GeneratorType.STONE);
        } else {
            return createGeneratorItem(plugin, org.bukkit.Material.OBSIDIAN, "§5§lObsydianarka",
                    ai.aisector.generators.GeneratorType.OBSIDIAN);
        }
    }


}
