package ai.aisector.sectors;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Tag;

public final class LockedSpawnListener implements Listener {
    private final SectorPlugin plugin;
    private final SectorManager sectors;

    public LockedSpawnListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectors = plugin.getSectorManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onButton(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Block button = event.getClickedBlock();
        if (button == null || !sectors.insideLockedSpawn(player.getLocation())
                || !sectors.insideLockedSpawn(button.getLocation()) || !Tag.BUTTONS.isTagged(button.getType())
                || !(button.getBlockData() instanceof Switch data)) return;
        BlockFace support = switch (data.getAttachedFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            case WALL -> data.getFacing().getOppositeFace();
        };
        if (button.getRelative(support).getType() != Material.SPONGE) return;
        event.setCancelled(true);
        if (plugin.getUserManager().getUser(player) == null || plugin.getUserManager().isTransferPending(player)) return;
        try {
            sectors.transferFromSpawnButton(player);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not prepare spawn exit for " + player.getUniqueId() + ": " + failure.getMessage());
            player.sendMessage(Messages.text("locked-spawn-listener.nie-udalo-sie-przygotowac-losowego-teleportu-sprobuj"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (sectors.isLockedSpawnBackend() && (event.getTo() == null || !sectors.insideLockedSpawn(event.getTo())))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (sectors.isLockedSpawnBackend()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (sectors.isLockedSpawnBackend() && event.getEntity() instanceof Player) event.setCancelled(true);
    }
}
