package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.generators.GeneratorItems;
import ai.aisector.generators.GeneratorManager;
import ai.aisector.generators.GeneratorType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GeneratorListener implements Listener {

    private final SectorPlugin plugin;
    private final GeneratorManager manager;

    public GeneratorListener(SectorPlugin plugin, GeneratorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack inHand = e.getItemInHand();
        if (inHand == null) return;
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null) return;

        String typeStr = meta.getPersistentDataContainer()
                .get(GeneratorItems.genKey(plugin), PersistentDataType.STRING);
        if (typeStr == null) return;

        GeneratorType type = GeneratorType.valueOf(typeStr);

        // Wymuś zgodność typu bloku z typem generatora
        if ((type == GeneratorType.STONE && e.getBlockPlaced().getType() != Material.STONE) ||
                (type == GeneratorType.OBSIDIAN && e.getBlockPlaced().getType() != Material.OBSIDIAN)) {
            e.setCancelled(true);
            return;
        }

        manager.register(e.getBlockPlaced().getLocation(), type); // upsert do Mongo
        e.getPlayer().sendMessage("§aPostawiono " + (type == GeneratorType.STONE ? "Stoniarkę" : "Obsydianarkę") + "!");
    }

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        if (!manager.isGenerator(b.getLocation())) return;

        org.bukkit.inventory.ItemStack hand =
                e.getPlayer().getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == org.bukkit.Material.GOLDEN_PICKAXE) {
            e.setCancelled(true); // zatrzymaj inne listenery

            ai.aisector.generators.GeneratorType type =
                    manager.getType(b.getLocation());

            // Usuń generator z pamięci i Mongo – brak regeneracji i brak wpisu po restarcie
            manager.unregister(b.getLocation());

            // Rozkręć blok i upuść gotowy item generatora z PDC
            b.setType(org.bukkit.Material.AIR);
            org.bukkit.inventory.ItemStack drop =
                    ai.aisector.generators.GeneratorItems.createItemForType(plugin, type);
            b.getWorld().dropItemNaturally(
                    b.getLocation().add(0.5, 0.25, 0.5), drop);

            e.getPlayer().sendMessage("§eZdemontowano " +
                    (type == ai.aisector.generators.GeneratorType.STONE ? "Stoniarkę" : "Obsydianarkę") +
                    " – możesz postawić ją ponownie gdzie indziej!");
            return;
        }

        // Zwykłe kopanie generatora – standardowa regeneracja
        manager.scheduleRegen(b);
    }



    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> manager.isGenerator(b.getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> manager.isGenerator(b.getLocation()))) {
            e.setCancelled(true);
        }
    }
}
