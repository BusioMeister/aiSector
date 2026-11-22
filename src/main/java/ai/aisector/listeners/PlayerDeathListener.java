package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.ranking.CombatRanking;
import ai.aisector.ranking.DeathAnnouncements;
import ai.aisector.utils.InventorySerializer;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.Date;
import java.util.UUID;

public class PlayerDeathListener implements Listener {

    // Potrzebujemy teraz wszystkich trzech managerów
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final SectorManager sectorManager;
    private final RedisManager redisManager; // <-- DODAJEMY POLE
    private final CombatRanking ranking;
    private final java.util.Set<java.util.concurrent.CompletableFuture<?>> backups = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public java.util.concurrent.CompletableFuture<Void> checkpoint() {
        return java.util.concurrent.CompletableFuture.allOf(backups.toArray(java.util.concurrent.CompletableFuture[]::new));
    }

    public PlayerDeathListener(SectorPlugin plugin, MongoDBManager mongoDBManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
        this.redisManager = plugin.getRedisManager(); // <-- INICJALIZUJEMY POLE
        this.ranking = new CombatRanking(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUuid = victim.getUniqueId();
        plugin.getCombatTitle().cancel(victimUuid);
        Location location = victim.getLocation();
        boolean combatEligibleFall = !plugin.isCombatLogoutDeath(victim) && DeathAnnouncements.isCombatFall(event);
        Player fallAttacker = combatEligibleFall ? plugin.lastCombatAttacker(victim) : null;
        if (fallAttacker != null) victim.setKiller(fallAttacker);
        Player killer = combatEligibleFall ? fallAttacker : victim.getKiller();
        event.deathMessage(null);
        DeathAnnouncements.publish(plugin, ranking, event, killer, fallAttacker != null);
        Integer refundableLoss = ranking.refundableLoss(victim, killer);
        if (killer != null && !killer.equals(victim)) {
            var notice = ranking.deathNotice(victim, killer, refundableLoss);
            plugin.getDeathNotices().put(victimUuid, notice);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> plugin.getDeathNotices().remove(victimUuid, notice), 12_000L);
        } else {
            plugin.getDeathNotices().remove(victimUuid);
        }
        location.getWorld().strikeLightningEffect(location);
        var savedStats = ranking.onDeath(victim, killer);
        String mappedSector = sectorManager.getSectorForLocation(location.getBlockX(), location.getBlockZ());
        String sectorName = mappedSector != null ? mappedSector : plugin.getConfig().getString("this-sector-name", "unknown");

        // 🔥 TWOJA ORYGINALNA LOGIKA: Zapisujemy sektor śmierci do celów respawnu 🔥
        plugin.getPlayerDeathSectors().put(victimUuid, sectorName);
        redisManager.executeAsync(jedis -> jedis.del("player:data:" + victimUuid))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not clear stale player data after death: " + error.getMessage());
                    return null;
                });

        // 🔥 NOWA LOGIKA: Zapisujemy pełny backup do bazy danych MongoDB 🔥
        // 1. Serializujemy ekwipunek
        try {
            String inventoryData = InventorySerializer.serializeInventory(
                    victim.getInventory().getStorageContents(), victim.getInventory().getArmorContents(),
                    victim.getInventory().getItemInOffHand());

            Document deathRecord = new Document();
            deathRecord.append("victim_uuid", victim.getUniqueId().toString());
            deathRecord.append("victim_name", victim.getName());
            deathRecord.append("timestamp", new Date());
            deathRecord.append("sector", sectorName);

            String killerName = "Środowisko";
            if (killer != null) {
                killerName = killer.getName();
            }
            deathRecord.append("killer_name", killerName);

            Document locationDoc = new Document("world", location.getWorld().getName())
                    .append("x", location.getBlockX()).append("y", location.getBlockY()).append("z", location.getBlockZ());
            deathRecord.append("location", locationDoc);
            deathRecord.append("inventory_data", inventoryData);
            deathRecord.append("restored_by", "Nikt");
            deathRecord.append("restored_at", null);

            var backup = savedStats.handle((ignored, error) -> {
                if (error == null && refundableLoss != null) deathRecord.append("refundableRating", refundableLoss);
                return deathRecord;
            }).thenCompose(record -> mongoDBManager.insertOneAsync("death_backups", record));
            backups.add(backup);
            backup.whenComplete((ignored, error) -> {
                backups.remove(backup);
                if (error == null) return;
                plugin.getLogger().severe("Could not save death backup for " + victimUuid + ": " + error.getMessage());
            });
        } catch (RuntimeException invalidInventory) {
            plugin.getLogger().severe("Could not capture death backup for " + victimUuid
                    + "; no usable backup record was created: " + invalidInventory.getMessage());
        }
    }
}
