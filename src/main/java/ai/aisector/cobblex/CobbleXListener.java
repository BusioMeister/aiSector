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
        this.plugin = plugin;
        this.manager = manager;
    }


    @EventHandler
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        org.bukkit.inventory.ItemStack inHand = e.getItemInHand();
        if (!ai.aisector.cobblex.CobbleXItems.isCobbleX(plugin, inHand)) return;

        // Anuluj stawianie (blok nie pojawi się w świecie)
        e.setCancelled(true);
        e.getBlockPlaced().setType(org.bukkit.Material.AIR);

        // 1) Zużyj 1 sztukę CobbleX z właściwej ręki
        org.bukkit.entity.Player p = e.getPlayer();
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        org.bukkit.inventory.EquipmentSlot slot = e.getHand();

        if (slot == org.bukkit.inventory.EquipmentSlot.HAND) {
            // Main hand
            if (inHand.getAmount() <= 1) {
                inv.setItemInMainHand(null);
            } else {
                inHand.setAmount(inHand.getAmount() - 1);
                inv.setItemInMainHand(inHand);
            }
        } else {
            // Off-hand
            org.bukkit.inventory.ItemStack off = inv.getItemInOffHand();
            if (off != null && ai.aisector.cobblex.CobbleXItems.isCobbleX(plugin, off)) {
                if (off.getAmount() <= 1) {
                    inv.setItemInOffHand(null);
                } else {
                    off.setAmount(off.getAmount() - 1);
                    inv.setItemInOffHand(off);
                }
            }
        }

        // 2) Losowanie nagrody
        java.util.Optional<org.bukkit.inventory.ItemStack> opt = manager.roll();
        if (opt.isPresent()) {
            org.bukkit.inventory.ItemStack reward = opt.get();

            // Dodaj do eq, a nadwyżki zrzuć na ziemię
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftovers = inv.addItem(reward);
            if (!leftovers.isEmpty()) {
                for (org.bukkit.inventory.ItemStack left : leftovers.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
            }
            p.sendMessage("§aCobbleX: §fotrzymano §e" + reward.getAmount() + "x §6" + reward.getType());
        } else {
            p.sendMessage("§cCobbleX: brak skonfigurowanych nagród.");
        }

        // 3) Particles
        e.getBlock().getWorld().spawnParticle(
                org.bukkit.Particle.CLOUD,
                e.getBlock().getLocation().add(0.5, 0.5, 0.5),
                40, 0.4, 0.4, 0.4, 0.01
        );
    }
}
