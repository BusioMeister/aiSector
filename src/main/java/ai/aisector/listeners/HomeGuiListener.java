package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.task.HomeTeleportWarmupTask;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.user.StoredHomeData;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HomeGuiListener implements Listener {

    private final MongoDBManager mongoDBManager;
    private final SectorManager sectorManager;
    private final SectorPlugin plugin;
    private final Set<java.util.UUID> pendingHomeLoads = ConcurrentHashMap.newKeySet();

    public HomeGuiListener(MongoDBManager mongoDBManager, SectorManager sectorManager, SectorPlugin plugin) {
        this.mongoDBManager = mongoDBManager;
        this.sectorManager = sectorManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8Wybierz dom do teleportacji")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || event.getSlot() < 2 || event.getSlot() > 6) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.GREEN_CONCRETE) return;

        int homeSlot = event.getSlot() - 1;
        java.util.UUID playerUuid = player.getUniqueId();
        if (!pendingHomeLoads.add(playerUuid)) return;
        mongoDBManager.findOneAsync("player_homes", Filters.and(
                        Filters.eq("player_uuid", playerUuid.toString()), Filters.eq("home_slot", homeSlot)))
                .whenComplete((home, error) -> {
                    boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                            pendingHomeLoads.remove(playerUuid);
                            Player currentPlayer = Bukkit.getPlayer(playerUuid);
                            if (currentPlayer == null || currentPlayer != player || !currentPlayer.isOnline()) return;
                            if (error != null || home == null) {
                                currentPlayer.sendMessage("§cWystąpił błąd podczas wczytywania tego domu.");
                                if (error != null) {
                                    plugin.getLogger().warning("Could not load home " + homeSlot + " for " + playerUuid
                                            + ": " + error.getMessage());
                                }
                                return;
                            }

                            final StoredHomeData storedHome;
                            try {
                                storedHome = StoredHomeData.parse(home);
                            } catch (RuntimeException invalidHome) {
                                currentPlayer.sendMessage("§cZapis tego domu jest nieprawidłowy.");
                                return;
                            }
                            if (storedHome.slot() != homeSlot) return;
                            currentPlayer.closeInventory();
                            JsonObject targetJson = new JsonObject();
                            targetJson.addProperty("world", storedHome.world());
                            targetJson.addProperty("x", storedHome.x());
                            targetJson.addProperty("y", storedHome.y());
                            targetJson.addProperty("z", storedHome.z());
                            targetJson.addProperty("yaw", storedHome.yaw());
                            targetJson.addProperty("pitch", storedHome.pitch());
                            new HomeTeleportWarmupTask(currentPlayer, storedHome.sector(), targetJson,
                                    homeSlot, plugin).start();
                    });
                    if (!scheduled) pendingHomeLoads.remove(playerUuid);
                });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals("§8Wybierz dom do teleportacji")) event.setCancelled(true);
    }
}
