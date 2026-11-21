package ai.aisector.listeners;

import ai.aisector.utils.Messages;

import ai.aisector.database.MongoDBManager;
import ai.aisector.SectorPlugin;
import ai.aisector.sectors.SectorManager;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.user.HomeSlotRepository;
import ai.aisector.user.HomeSlotMenu;
import org.bukkit.Bukkit;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SetHomeGuiListener implements Listener {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final HomeSlotRepository homes;
    private final Set<String> pendingHomeOperations = ConcurrentHashMap.newKeySet();

    public SetHomeGuiListener(SectorPlugin plugin, MongoDBManager mongoDBManager, SectorManager sectorManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.homes = new HomeSlotRepository(mongoDBManager);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HomeSlotMenu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || event.getSlot() < 2 || event.getSlot() > 6) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (!(event.getView().getTopInventory().getHolder() instanceof HomeSlotMenu menu)
                || !menu.belongsTo(player.getUniqueId())) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !(clickedItem.getType() == Material.RED_CONCRETE || clickedItem.getType() == Material.GREEN_CONCRETE)) return;

        int slot = event.getSlot() - 1; // Sloty w GUI 2-6 -> sloty domu 1-5
        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();
        String operationKey = playerUuid + ":" + slot;
        if (!pendingHomeOperations.add(operationKey)) {
            player.sendMessage(Messages.text("set-home-gui-listener.ta-operacja-na-tym-slocie-domu-jest"));
            return;
        }

        if (clickedItem.getType() == Material.RED_CONCRETE && event.isRightClick()) {
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
            homes.createAsync(player.getUniqueId(), homeDoc)
                    .whenComplete((ignored, error) -> scheduleCompletion(operationKey, () -> {
                        Player currentPlayer = Bukkit.getPlayer(java.util.UUID.fromString(playerUuid));
                        if (currentPlayer == null || currentPlayer != player || !currentPlayer.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(Messages.text("set-home-gui-listener.nie-udalo-sie-zapisac-domu"));
                            plugin.getLogger().warning("Could not save home for " + playerName + ": "
                                    + error.getMessage());
                            return;
                        }
                        currentPlayer.sendMessage(Messages.text("set-home-gui-listener.pomyslnie-ustawiono-dom", slot));
                        currentPlayer.closeInventory();
                    }));
        } else if (clickedItem.getType() == Material.GREEN_CONCRETE && event.isRightClick()) {
            // Usuwanie domu
            homes.deleteAsync(player.getUniqueId(), slot, menu.observedSlot(slot))
                    .whenComplete((ignored, error) -> scheduleCompletion(operationKey, () -> {
                        Player currentPlayer = Bukkit.getPlayer(java.util.UUID.fromString(playerUuid));
                        if (currentPlayer == null || currentPlayer != player || !currentPlayer.isOnline()) return;
                        if (error != null) {
                            currentPlayer.sendMessage(Messages.text("set-home-gui-listener.nie-udalo-sie-usunac-domu"));
                            plugin.getLogger().warning("Could not delete home for " + playerName + ": "
                                    + error.getMessage());
                            return;
                        }
                        currentPlayer.sendMessage(Messages.text("set-home-gui-listener.pomyslnie-usunieto-dom", slot));
                        currentPlayer.closeInventory();
                    }));
        } else {
            pendingHomeOperations.remove(operationKey);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomeSlotMenu) event.setCancelled(true);
    }

    private void scheduleCompletion(String operationKey, Runnable completion) {
        if (!BukkitTaskScheduler.execute(plugin, () -> {
            pendingHomeOperations.remove(operationKey);
            completion.run();
        })) pendingHomeOperations.remove(operationKey);
    }
}
