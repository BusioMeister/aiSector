package ai.aisector.cobblex;

import ai.aisector.SectorPlugin;
import org.bukkit.Particle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class CobbleXListener implements Listener {
    private final SectorPlugin plugin;
    private final CobbleXManager manager;

    public CobbleXListener(SectorPlugin plugin, CobbleXManager manager) {
        this.plugin = plugin; this.manager = manager;
    }


    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack inHand = e.getItemInHand();
        if (!CobbleXItems.isCobbleX(plugin, inHand)) return;

        // „Zużyj” blok: anuluj stawianie i zostaw powietrze
        e.setCancelled(true);
        e.getBlockPlaced().setType(org.bukkit.Material.AIR);

        // Losowanie
        var opt = manager.roll();
        if (opt.isPresent()) {
            var reward = opt.get();
            e.getPlayer().getInventory().addItem(reward);
            e.getPlayer().sendMessage("§aCobbleX: §fotrzymano §e" + reward.getAmount() + "x §6" + reward.getType());
        } else {
            e.getPlayer().sendMessage("§cCobbleX: brak skonfigurowanych nagród.");
        }

        // Particles
        e.getBlock().getWorld().spawnParticle(Particle.CLOUD, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 40, 0.4, 0.4, 0.4, 0.01);
    }
}
