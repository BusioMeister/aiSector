package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.task.HomeTeleportWarmupTask;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class HomeGuiListener implements Listener {

    private final MongoDBManager mongoDBManager;
    private final SectorManager sectorManager;
    private final SectorPlugin plugin;

    public HomeGuiListener(MongoDBManager mongoDBManager, SectorManager sectorManager, SectorPlugin plugin) {
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8Wybierz dom do teleportacji")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.GREEN_CONCRETE) return;

        int homeSlot = event.getSlot() - 1;
        Document home = mongoDBManager.getCollection("player_homes").find(
                Filters.and(Filters.eq("player_uuid", player.getUniqueId().toString()), Filters.eq("home_slot", homeSlot))
        ).first();

        if (home == null) {
            player.sendMessage("§cWystąpił błąd podczas wczytywania tego domu.");
            return;
        }

        player.closeInventory();

        // 🔥 KLUCZOWA ZMIANA: Zamiast teleportować, uruchamiamy zadanie z odliczaniem 🔥
        String targetSector = home.getString("sector");
        Document locDoc = home.get("location", Document.class);

        JsonObject targetJson = new JsonObject();
        targetJson.addProperty("world", locDoc.getString("world"));
        targetJson.addProperty("x", locDoc.getDouble("x"));
        targetJson.addProperty("y", locDoc.getDouble("y"));
        targetJson.addProperty("z", locDoc.getDouble("z"));
        targetJson.addProperty("yaw", locDoc.getDouble("yaw"));
        targetJson.addProperty("pitch", locDoc.getDouble("pitch"));

        new HomeTeleportWarmupTask(player, targetSector, targetJson, homeSlot, plugin).start();
    }
}