package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.utils.InventorySerializer;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Date;

public class PlayerDeathListener implements Listener {

    // Potrzebujemy teraz wszystkich trzech managerów
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final SectorManager sectorManager;

    public PlayerDeathListener(SectorPlugin plugin, MongoDBManager mongoDBManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Location location = victim.getLocation();
        String sectorName = sectorManager.getSectorForLocation(location.getBlockX(), location.getBlockZ());

        // 🔥 TWOJA ORYGINALNA LOGIKA: Zapisujemy sektor śmierci do celów respawnu 🔥
        plugin.getPlayerDeathSectors().put(victim.getUniqueId(), sectorName);

        // 🔥 NOWA LOGIKA: Zapisujemy pełny backup do bazy danych MongoDB 🔥
        // 1. Serializujemy ekwipunek
        String inventoryData = InventorySerializer.serializeInventory(
                victim.getInventory().getContents(),
                victim.getInventory().getArmorContents(),
                victim.getInventory().getItemInOffHand()
        );

        // 2. Przygotowujemy dokument do zapisu
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
            if (msg.contains("by")) {
                killerName = msg.substring(msg.lastIndexOf("by") + 3).trim();
            }
        }
        deathRecord.append("killer_name", killerName);

        Document locationDoc = new Document();
        locationDoc.append("world", location.getWorld().getName());
        locationDoc.append("x", location.getBlockX());
        locationDoc.append("y", location.getBlockY());
        locationDoc.append("z", location.getBlockZ());
        deathRecord.append("location", locationDoc);

        deathRecord.append("inventory_data", inventoryData);
        deathRecord.append("restored_by", "Nikt");
        deathRecord.append("restored_at", null);

        // 3. Zapisujemy dokument w kolekcji 'death_backups'
        mongoDBManager.insertOne("death_backups", deathRecord);
    }
}