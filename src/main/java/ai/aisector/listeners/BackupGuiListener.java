package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.InventorySerializer;
import ai.aisector.utils.BukkitTaskScheduler;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import redis.clients.jedis.params.SetParams;

public final class BackupGuiListener implements Listener {
    private static final int MAX_BACKUP_JSON_CHARS = 1_048_576;
    private static final class PendingBackupExistsException extends IllegalStateException {
        private PendingBackupExistsException(UUID victimUuid) {
            super("A pending backup already exists for " + victimUuid);
        }
    }
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final RedisManager redisManager;

    public BackupGuiListener(SectorPlugin plugin, MongoDBManager mongoDBManager, RedisManager redisManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
        this.redisManager = redisManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = event.getView().getTitle();
        if (title.startsWith("§8Backupy gracza: §c")) {
            handleMainGuiClick(event, admin);
        } else if (title.startsWith("§8Podgląd zgonu: ")) {
            handleViewGuiClick(event, admin);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith("§8Backupy gracza: §c") || title.startsWith("§8Podgląd zgonu: ")) {
            event.setCancelled(true);
        }
    }

    private void handleMainGuiClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD
                || !clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) return;

        String idLine = meta.getLore().get(meta.getLore().size() - 1);
        if (!idLine.startsWith("§0ID:")) return;
        ObjectId recordId;
        try {
            recordId = new ObjectId(idLine.substring(5));
        } catch (IllegalArgumentException exception) {
            admin.sendMessage("§cNieprawidłowy identyfikator backupu.");
            return;
        }

        boolean viewOnly = event.isRightClick();
        boolean restoreClick = event.isLeftClick();
        UUID adminUuid = admin.getUniqueId();
        mongoDBManager.findOneAsync("death_backups", Filters.eq("_id", recordId))
                .whenComplete((record, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentAdmin = Bukkit.getPlayer(adminUuid);
                    if (currentAdmin != admin || !admin.isOnline()) return;
                    if (error != null) {
                        currentAdmin.sendMessage("§cNie udało się odczytać backupu.");
                    } else if (record == null) {
                        currentAdmin.sendMessage("§cWystąpił błąd: Nie znaleziono tego zapisu śmierci.");
                    } else if (viewOnly) {
                        openBackupView(currentAdmin, record);
                    } else if (restoreClick) {
                        restoreBackup(currentAdmin, recordId, record);
                    }
                }));
    }

    private void openBackupView(Player admin, Document record) {
        String targetName = record.getString("victim_name");
        String invData = record.getString("inventory_data");
        if (invData == null || invData.length() > MAX_BACKUP_JSON_CHARS) {
            admin.sendMessage("§cDane ekwipunku backupu są puste lub zbyt duże.");
            return;
        }
        try {
            Inventory viewGui = InventorySerializer.createGuiFromJson(invData, "§8Podgląd zgonu: " + targetName);

            ItemStack backButton = new ItemStack(Material.BARRIER);
            ItemMeta backMeta = backButton.getItemMeta();
            backMeta.setDisplayName("§cPowrót");
            backMeta.setLore(Collections.singletonList("§7Kliknij, aby wrócić do listy zgonów."));
            backButton.setItemMeta(backMeta);
            viewGui.setItem(44, backButton);

            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
            for (int i = 41; i < 44; i++) viewGui.setItem(i, filler);
            admin.openInventory(viewGui);
        } catch (IOException | RuntimeException exception) {
            admin.sendMessage("§cWystąpił błąd podczas odczytu ekwipunku.");
            plugin.getLogger().warning("Could not read death backup inventory: " + exception.getMessage());
        }
    }

    private void restoreBackup(Player admin, ObjectId recordId, Document record) {
        String restoredBy = record.getString("restored_by");
        if (restoredBy != null && !restoredBy.equals("Nikt")) {
            admin.sendMessage("§cTen ekwipunek został już przywrócony przez §e" + restoredBy + "§c.");
            return;
        }

        String targetName = record.getString("victim_name");
        String victimUuid = record.getString("victim_uuid");
        String inventoryData = record.getString("inventory_data");
        if (inventoryData == null || inventoryData.length() > MAX_BACKUP_JSON_CHARS) {
            admin.sendMessage("§cDane ekwipunku backupu są puste lub zbyt duże.");
            return;
        }
        UUID victimId;
        try {
            victimId = UUID.fromString(victimUuid);
        } catch (RuntimeException exception) {
            admin.sendMessage("§cBackup zawiera nieprawidłowy UUID gracza.");
            return;
        }
        InventorySerializer.PreparedInventory preparedInventory;
        try {
            preparedInventory = InventorySerializer.prepareInventory(inventoryData);
        } catch (RuntimeException malformedInventory) {
            admin.sendMessage("§cDane ekwipunku backupu są uszkodzone; backup nie został oznaczony jako przywrócony.");
            plugin.getLogger().warning("Refusing malformed death backup " + recordId + ": "
                    + malformedInventory.getMessage());
            return;
        }
        UUID adminUuid = admin.getUniqueId();
        String adminName = admin.getName();
        String claimToken = UUID.randomUUID().toString();
        Player victim = Bukkit.getPlayer(victimId);

        if (victim == null || !victim.isOnline()) {
            saveOfflineBackup(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
            return;
        }

        markRestoredAsync(recordId, adminName, claimToken).whenComplete((result, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentAdmin = Bukkit.getPlayer(adminUuid);
                    if (error != null) {
                        messageIfOnline(currentAdmin, "§cNie udało się oznaczyć backupu jako przywróconego.");
                        return;
                    }
                    if (result.getModifiedCount() == 0) {
                        messageIfOnline(currentAdmin, "§cTen ekwipunek został już przywrócony przez innego administratora.");
                        return;
                    }

                    Player currentVictim = Bukkit.getPlayer(victimId);
                    if (currentVictim == null || !currentVictim.isOnline()) {
                        savePendingAfterRestore(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
                        return;
                    }
                    if (plugin.getUserManager().isTransferPending(currentVictim)) {
                        rollbackRestoreMarker(recordId, adminName, claimToken);
                        messageIfOnline(currentAdmin, "§cGracz jest w trakcie transferu. Ponów przywrócenie po jego zakończeniu.");
                        return;
                    }
                    try {
                        preparedInventory.applyTo(currentVictim.getInventory());
                    } catch (RuntimeException applyError) {
                        plugin.getLogger().severe("Backup apply failed after durable claim " + recordId
                                + "; manual review required: " + applyError.getMessage());
                        messageIfOnline(currentAdmin, "§cNie udało się potwierdzić przywrócenia; wymagana ręczna weryfikacja.");
                        return;
                    }
                    currentVictim.sendMessage("§aTwój ekwipunek ze śmierci został przywrócony przez administratora.");
                    finishRestore(currentAdmin, targetName);
                });
                if (!scheduled && error == null && result != null && result.getModifiedCount() > 0) {
                    savePendingAfterRestore(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
                }
        });
    }

    private void savePendingAfterRestore(UUID adminUuid, String adminName, String claimToken, ObjectId recordId, String targetName,
                                         UUID victimUuid, String inventoryData) {
        storePendingBackupIfAbsent(victimUuid, inventoryData)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        boolean pendingBackupExists = isPendingBackupExists(error);
                        if (pendingBackupExists) rollbackRestoreMarker(recordId, adminName, claimToken);
                        BukkitTaskScheduler.execute(plugin, () -> {
                            Player currentAdmin = Bukkit.getPlayer(adminUuid);
                            messageIfOnline(currentAdmin, pendingBackupExists
                                    ? "§cGracz ma już oczekujący backup; obecny backup nie został nadpisany."
                                    : "§cNie można potwierdzić zapisu oczekującego backupu. "
                                    + "Marker pozostaje zablokowany do ręcznej weryfikacji.");
                        });
                        return;
                    }
                    BukkitTaskScheduler.execute(plugin,
                            () -> finishRestore(Bukkit.getPlayer(adminUuid), targetName));
                });
    }

    private void saveOfflineBackup(UUID adminUuid, String adminName, String claimToken, ObjectId recordId, String targetName,
                                   UUID victimUuid, String inventoryData) {
        markRestoredAsync(recordId, adminName, claimToken)
                .thenCompose(result -> {
                    if (result == null || result.getModifiedCount() == 0) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Backup was already restored"));
                    }
                    // Commit the Mongo claim before making the payload visible to the joining player.
                    return storePendingBackupIfAbsent(victimUuid, inventoryData);
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        boolean pendingBackupExists = isPendingBackupExists(error);
                        if (pendingBackupExists) {
                            rollbackRestoreMarker(recordId, adminName, claimToken);
                        }
                        BukkitTaskScheduler.execute(plugin, () -> {
                            Player currentAdmin = Bukkit.getPlayer(adminUuid);
                            if (currentAdmin != null) {
                                currentAdmin.sendMessage(pendingBackupExists
                                        ? "§cGracz ma już oczekujący backup; obecny backup nie został nadpisany."
                                        : "§cNie udało się zakończyć przywracania backupu. "
                                        + "Sprawdź jego stan przed ponowieniem operacji.");
                            }
                        });
                        if (pendingBackupExists) {
                            plugin.getLogger().warning("Offline backup restore found an existing pending backup for "
                                    + recordId + "; the payload was retained and token-specific claim rollback was requested.");
                        } else {
                            plugin.getLogger().severe("Offline backup restore did not complete for " + recordId
                                    + "; pending write outcome may be uncertain: " + error.getMessage());
                        }
                        return;
                    }
                    BukkitTaskScheduler.execute(plugin,
                            () -> finishRestore(Bukkit.getPlayer(adminUuid), targetName));
                });
    }

    private CompletableFuture<Void> storePendingBackupIfAbsent(UUID victimUuid, String inventoryData) {
        String key = "player:pending_backup:" + victimUuid;
        return redisManager.executeAsync(jedis -> jedis.set(key, inventoryData,
                        SetParams.setParams().nx().ex(86400)))
                .thenApply(status -> {
                    if (!"OK".equals(status)) {
                        throw new PendingBackupExistsException(victimUuid);
                    }
                    return null;
                });
    }

    private void rollbackRestoreMarker(ObjectId recordId, String adminName, String claimToken) {
        mongoDBManager.updateOneAsync("death_backups",
                         BackupRestoreClaim.rollbackFilter(recordId, adminName, claimToken),
                        Updates.combine(Updates.set("restored_by", "Nikt"), Updates.unset("restored_at"),
                                Updates.unset("restore_claim")))
                .exceptionally(rollbackError -> {
                    plugin.getLogger().severe("Could not roll back backup restore marker " + recordId + ": "
                            + rollbackError.getMessage());
                    return null;
                });
    }

    private boolean isPendingBackupExists(Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != cause) {
            if (cause instanceof PendingBackupExistsException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private CompletableFuture<UpdateResult> markRestoredAsync(ObjectId recordId, String adminName, String claimToken) {
        return mongoDBManager.updateOneAsync("death_backups",
                Filters.and(Filters.eq("_id", recordId), Filters.or(
                                Filters.eq("restored_by", "Nikt"), Filters.exists("restored_by", false),
                                Filters.eq("restored_by", null))),
                Updates.combine(Updates.set("restored_by", adminName), Updates.set("restored_at", new Date()),
                        Updates.set("restore_claim", claimToken)));
    }

    private void finishRestore(Player admin, String targetName) {
        if (admin == null) return;
        admin.sendMessage("§aPomyślnie przywrócono backup dla gracza §e" + targetName + "§a.");
        admin.closeInventory();
    }

    private void messageIfOnline(Player player, String message) {
        if (player != null) player.sendMessage(message);
    }

    private void handleViewGuiClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getSlot() == 44 && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.BARRIER) {
            String targetName = event.getView().getTitle().substring(17);
            admin.performCommand("backup " + targetName);
        }
    }
}
