package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ShutdownCheckpoint extends JedisPubSub implements Listener {
    public static final String CHANNEL = "aisector:shutdown:checkpoint";
    private final SectorPlugin plugin;
    private final String sector;
    private UUID draining;
    private boolean confirmed;

    public ShutdownCheckpoint(SectorPlugin plugin) {
        this.plugin = plugin;
        sector = plugin.getConfig().getString("this-sector-name", "");
    }

    public boolean draining() { return draining != null; }
    public boolean confirmed() { return confirmed; }

    @Override public void onMessage(String channel, String message) {
        if (!CHANNEL.equals(channel) || message == null || message.length() > 44) return;
        String[] fields = message.split(":", 2);
        if (fields.length != 2 || !fields[0].equals("save") && !fields[0].equals("cancel")) return;
        final UUID id;
        try { id = UUID.fromString(fields[1]); }
        catch (IllegalArgumentException invalid) { return; }
        BukkitTaskScheduler.execute(plugin, () -> {
            if (fields[0].equals("save")) prepare(id);
            else if (id.equals(draining)) clear();
        });
    }

    private void prepare(UUID id) {
        if (draining != null) return;
        draining = id;
        // Never strand players if Velocity or its maintenance script disappears.
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (id.equals(draining)) clear(); }, 1200L);
        try {
            Bukkit.getOnlinePlayers().forEach(Player::closeInventory);
            Bukkit.getServer().savePlayers();
            for (var world : Bukkit.getWorlds()) world.save(true);
            CompletableFuture.allOf(plugin.getUserManager().checkpoint(), plugin.getGuildManager().checkpoint(),
                    plugin.getGeneratorManager().checkpoint(), plugin.pendingDeathBackups())
                    .whenComplete((ignored, error) -> BukkitTaskScheduler.execute(plugin, () -> finish(id, error)));
        } catch (RuntimeException error) {
            finish(id, error);
        }
    }

    private void finish(UUID id, Throwable error) {
        if (!id.equals(draining)) return;
        confirmed = error == null;
        if (error != null) plugin.getLogger().warning("Shutdown checkpoint " + id + " failed: " + error);
        plugin.getRedisManager().executeAsync(jedis -> {
            String key = CHANNEL + ":" + id;
            jedis.hset(key, sector, error == null ? "saved" : "failed");
            jedis.expire(key, 90);
            return null;
        });
    }

    private void clear() { draining = null; confirmed = false; }

    @EventHandler(priority = EventPriority.LOWEST) public void move(PlayerMoveEvent e) {
        if (draining() && e.hasChangedPosition()) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void interact(PlayerInteractEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void interactEntity(PlayerInteractEntityEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void damage(EntityDamageEvent e) {
        if (draining() && e.getEntity() instanceof Player) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void click(InventoryClickEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void drag(InventoryDragEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void drop(PlayerDropItemEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void swap(PlayerSwapHandItemsEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void consume(PlayerItemConsumeEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void pickup(EntityPickupItemEvent e) {
        if (draining() && e.getEntity() instanceof Player) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void place(BlockPlaceEvent e) { if (draining()) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void destroy(BlockBreakEvent e) { if (draining()) e.setCancelled(true); }
}
