package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.generators.GeneratorItems;
import ai.aisector.generators.GeneratorManager;
import ai.aisector.generators.GeneratorType;
import ai.aisector.generators.GeneratorLifecyclePolicy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack inHand = e.getItemInHand();
        if (inHand == null) return;
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null) return;

        String typeStr = meta.getPersistentDataContainer()
                .get(GeneratorItems.genKey(plugin), PersistentDataType.STRING);
        if (typeStr == null) return;

        final GeneratorType type;
        try {
            Integer version = meta.getPersistentDataContainer()
                    .get(GeneratorItems.versionKey(plugin), PersistentDataType.INTEGER);
            type = ai.aisector.generators.GeneratorItemData
                    .parse(typeStr, version, inHand.getType()).type();
        } catch (IllegalArgumentException invalidType) {
            e.setCancelled(true);
            return;
        }

        // Wymuś zgodność typu bloku z typem generatora
        if ((type == GeneratorType.STONE && e.getBlockPlaced().getType() != Material.STONE) ||
                (type == GeneratorType.OBSIDIAN && e.getBlockPlaced().getType() != Material.OBSIDIAN)) {
            e.setCancelled(true);
            return;
        }

        manager.register(e.getBlockPlaced().getLocation(), type); // upsert do Mongo
        e.getPlayer().sendMessage("§aPostawiono " + (type == GeneratorType.STONE ? "Stoniarkę" : "Obsydianarkę") + "!");
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        if (!manager.isGenerator(b.getLocation())) return;

        org.bukkit.inventory.ItemStack hand =
                e.getPlayer().getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == org.bukkit.Material.GOLDEN_PICKAXE) {
            e.setCancelled(true); // zatrzymaj inne listenery

            ai.aisector.generators.GeneratorType type =
                    manager.getType(b.getLocation());

            if (type == null) return;
            java.util.UUID playerId = e.getPlayer().getUniqueId();
            org.bukkit.entity.Player player = e.getPlayer();
            manager.unregisterAsync(b.getLocation()).whenComplete((removed, error) ->
                    ai.aisector.utils.BukkitTaskScheduler.execute(plugin, () -> {
                        boolean deleteConfirmed = error == null && Boolean.TRUE.equals(removed);
                        if (!deleteConfirmed) {
                            if (player.isOnline() && player.getUniqueId().equals(playerId)) {
                                player.sendMessage("§cNie udało się trwale zdemontować generatora; spróbuj ponownie.");
                            }
                            return;
                        }
                        if (!GeneratorLifecyclePolicy.shouldDeliverDismantle(type, deleteConfirmed, b.getType())) return;
                        b.setType(org.bukkit.Material.AIR);
                        org.bukkit.inventory.ItemStack drop = GeneratorItems.createItemForType(plugin, type);
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.25, 0.5), drop);
                        if (player.isOnline() && player.getUniqueId().equals(playerId)) {
                            player.sendMessage("§eZdemontowano "
                                    + (type == GeneratorType.STONE ? "Stoniarkę" : "Obsydianarkę")
                                    + " – możesz postawić ją ponownie gdzie indziej!");
                        }
                    }));
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

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (manager.isGenerator(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.isGenerator(block.getLocation()));
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.isGenerator(block.getLocation()));
    }
}
