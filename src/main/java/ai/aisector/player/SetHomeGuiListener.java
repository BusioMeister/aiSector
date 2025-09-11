package ai.aisector.player;

import ai.aisector.database.MongoDBManager;
import ai.aisector.sectors.SectorManager;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SetHomeGuiListener implements Listener {

    private final MongoDBManager mongoDBManager;
    private final SectorManager sectorManager;

    public SetHomeGuiListener(MongoDBManager mongoDBManager, SectorManager sectorManager) {
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8Ustaw swój dom")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !(clickedItem.getType() == Material.RED_CONCRETE || clickedItem.getType() == Material.GREEN_CONCRETE)) return;

        int slot = event.getSlot() - 1; // Sloty w GUI 2-6 -> sloty domu 1-5

        if (clickedItem.getType() == Material.RED_CONCRETE) {
            // Ustawianie nowego domu
            Location loc = player.getLocation();
            Document homeDoc = new Document()
                    .append("player_uuid", player.getUniqueId().toString())
                    .append("home_slot", slot)
                    .append("sector", sectorManager.getSectorForLocation(loc.getBlockX(), loc.getBlockZ()))
                    .append("location", new Document()
                            .append("world", loc.getWorld().getName())
                            .append("x", loc.getX())
                            .append("y", loc.getY())
                            .append("z", loc.getZ())
                            .append("yaw", loc.getYaw())
                            .append("pitch", loc.getPitch()));
            mongoDBManager.insertOne("player_homes", homeDoc);
            player.sendMessage("§aPomyślnie ustawiono dom #" + slot + "!");
            player.closeInventory();
        } else if (clickedItem.getType() == Material.GREEN_CONCRETE && event.isRightClick()) {
            // Usuwanie domu
            mongoDBManager.getCollection("player_homes").deleteOne(
                    Filters.and(
                            Filters.eq("player_uuid", player.getUniqueId().toString()),
                            Filters.eq("home_slot", slot)
                    )
            );
            player.sendMessage("§aPomyślnie usunięto dom #" + slot + "!");
            player.closeInventory();
        }
    }
}