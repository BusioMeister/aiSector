package ai.aisector.user;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents normal player actions from changing a captured snapshot while its handoff is pending. */
final class TransferSnapshotGuard implements Listener {
    static final class Seal {
        final Player player;
        volatile boolean saved;
        Seal(Player player) { this.player = player; }
    }
    private final Map<UUID, Seal> seals = new ConcurrentHashMap<>();
    private final SectorPlugin plugin;

    TransferSnapshotGuard(SectorPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    Seal begin(Player player) {
        Seal seal = new Seal(player);
        if (seals.putIfAbsent(player.getUniqueId(), seal) != null)
            throw new IllegalStateException("A transfer snapshot is already pending");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (seals.get(player.getUniqueId()) == seal && player.isOnline()
                    && Bukkit.getPlayer(player.getUniqueId()) == player) {
                // Never release a live source while an already published snapshot may still be consumed.
                player.kickPlayer("Transfer nie został zakończony. Połącz się ponownie.");
            }
        }, 1200L);
        return seal;
    }

    void releaseUnpublished(Seal seal) { seals.remove(seal.player.getUniqueId(), seal); }
    boolean active(Player player) {
        Seal seal = seals.get(player.getUniqueId());
        return seal != null && seal.player == player;
    }
    boolean finish(Player player) {
        Seal seal = seals.get(player.getUniqueId());
        return seal != null && seal.player == player && seals.remove(player.getUniqueId(), seal) && seal.saved;
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void interactEntity(PlayerInteractEntityEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void consume(PlayerItemConsumeEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void attack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        org.bukkit.entity.Entity attacker = e.getDamager();
        if (attacker instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) attacker = p;
        if (attacker instanceof Player p && active(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void swap(PlayerSwapHandItemsEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void slot(PlayerItemHeldEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void food(FoodLevelChangeEvent e) { if (e.getEntity() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void experience(PlayerExpChangeEvent e) { if (active(e.getPlayer())) e.setAmount(0); }
    @EventHandler(priority = EventPriority.HIGHEST) public void place(BlockPlaceEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void move(PlayerMoveEvent e) { if (active(e.getPlayer())) e.setTo(e.getFrom()); }
}
