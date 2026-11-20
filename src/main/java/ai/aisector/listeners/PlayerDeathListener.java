package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.UserManager;
import ai.aisector.utils.InventorySerializer;
import ai.aisector.user.User;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
    private final UserManager userManager; // NOWE

    public PlayerDeathListener(SectorPlugin plugin, MongoDBManager mongoDBManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
        this.redisManager = plugin.getRedisManager(); // <-- INICJALIZUJEMY POLE
        this.userManager = plugin.getUserManager(); // NOWE
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUuid = victim.getUniqueId();
        Location location = victim.getLocation();
        String sectorName = sectorManager.getSectorForLocation(location.getBlockX(), location.getBlockZ());

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
            if (victim.getKiller() != null) {
                killerName = victim.getKiller().getName();
            } else if (event.getDeathMessage() != null) {
                String msg = event.getDeathMessage();
                if (msg.contains("by")) killerName = msg.substring(msg.lastIndexOf("by") + 3).trim();
            }
            deathRecord.append("killer_name", killerName);

            Document locationDoc = new Document("world", location.getWorld().getName())
                    .append("x", location.getBlockX()).append("y", location.getBlockY()).append("z", location.getBlockZ());
            deathRecord.append("location", locationDoc);
            deathRecord.append("inventory_data", inventoryData);
            deathRecord.append("restored_by", "Nikt");
            deathRecord.append("restored_at", null);

            mongoDBManager.insertOneAsync("death_backups", deathRecord).exceptionally(error -> {
                plugin.getLogger().severe("Could not save death backup for " + victimUuid + ": " + error.getMessage());
                return null;
            });
        } catch (RuntimeException invalidInventory) {
            plugin.getLogger().severe("Could not capture death backup for " + victimUuid
                    + "; no usable backup record was created: " + invalidInventory.getMessage());
        }
        // === STATYSTYKI PVP ===
        // Ofiara
        User victimUser = userManager.getUser(victim);
        if (victimUser != null) {
            victimUser.addDeath();
            userManager.saveCombatStats(victim, victimUser);
        }

        // Zabójca (może być null, jeśli środowisko)
        Player killer = victim.getKiller();
        if (killer != null) {
            User killerUser = userManager.getUser(killer);
            if (killerUser != null) {
                killerUser.addKill();
                userManager.saveCombatStats(killer, killerUser);
            }
        }
    }
}
