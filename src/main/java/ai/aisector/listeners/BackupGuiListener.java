package ai.aisector.listeners;

import ai.aisector.utils.Messages;
import ai.aisector.utils.PluginMenu;

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
        var inventory = event.getView().getTopInventory();
        if (PluginMenu.is(inventory, PluginMenu.Kind.BACKUPS)) {
            handleMainGuiClick(event, admin);
        } else if (PluginMenu.is(inventory, PluginMenu.Kind.BACKUP_VIEW)) {
            handleViewGuiClick(event, admin);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        var inventory = event.getView().getTopInventory();
        if (PluginMenu.is(inventory, PluginMenu.Kind.BACKUPS) || PluginMenu.is(inventory, PluginMenu.Kind.BACKUP_VIEW)) {
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
        if (meta == null) return;
        String id = meta.getPersistentDataContainer().get(org.bukkit.NamespacedKey.fromString("aisector:backup-id"),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null) return;
        ObjectId recordId;
        try {
            recordId = new ObjectId(id);
        } catch (IllegalArgumentException exception) {
            admin.sendMessage(Messages.text("backup-gui-listener.nieprawidlowy-identyfikator-backupu"));
            return;
        }

        boolean viewOnly = event.isRightClick();
        boolean restoreClick = event.isLeftClick();
        boolean restoreStats = event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
        UUID adminUuid = admin.getUniqueId();
        mongoDBManager.findOneAsync("death_backups", Filters.eq("_id", recordId))
                .whenComplete((record, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentAdmin = Bukkit.getPlayer(adminUuid);
                    if (currentAdmin != admin || !admin.isOnline()) return;
                    if (error != null) {
                        currentAdmin.sendMessage(Messages.text("backup-gui-listener.nie-udalo-sie-odczytac-backupu"));
                    } else if (record == null) {
                        currentAdmin.sendMessage(Messages.text("backup-gui-listener.wystapil-blad-nie-znaleziono-tego-zapisu-smierci"));
                    } else if (viewOnly) {
                        openBackupView(currentAdmin, record);
                    } else if (restoreClick) {
                        restoreBackup(currentAdmin, recordId, record, restoreStats);
                    }
                }));
    }

    private void openBackupView(Player admin, Document record) {
        String targetName = record.getString("victim_name");
        String invData = record.getString("inventory_data");
        if (invData == null || invData.length() > MAX_BACKUP_JSON_CHARS) {
            admin.sendMessage(Messages.text("backup-gui-listener.dane-ekwipunku-backupu-sa-puste-lub-zbyt"));
            return;
        }
        try {
            Inventory viewGui = InventorySerializer.fillGuiFromJson(invData, new PluginMenu(
                    PluginMenu.Kind.BACKUP_VIEW, targetName, 45, Messages.text("backup-gui-listener.preview-title", targetName)).getInventory());

            ItemStack backButton = new ItemStack(Material.BARRIER);
            ItemMeta backMeta = backButton.getItemMeta();
            backMeta.setDisplayName(Messages.text("backup-gui-listener.powrot"));
            backMeta.setLore(Collections.singletonList(Messages.text("backup-gui-listener.kliknij-aby-wrocic-do-listy-zgonow")));
            backButton.setItemMeta(backMeta);
            viewGui.setItem(44, backButton);

            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
            for (int i = 41; i < 44; i++) viewGui.setItem(i, filler);
            admin.openInventory(viewGui);
        } catch (IOException | RuntimeException exception) {
            admin.sendMessage(Messages.text("backup-gui-listener.wystapil-blad-podczas-odczytu-ekwipunku"));
            plugin.getLogger().warning("Could not read death backup inventory: " + exception.getMessage());
        }
    }

    private void restoreBackup(Player admin, ObjectId recordId, Document record, boolean restoreStats) {
        String restoredBy = record.getString("restored_by");
        if (restoredBy != null && !restoredBy.equals("Nikt")) {
            admin.sendMessage(Messages.text("backup-gui-listener.ten-ekwipunek-zostal-juz-przywrocony-przez", restoredBy));
            return;
        }

        String targetName = record.getString("victim_name");
        String victimUuid = record.getString("victim_uuid");
        String inventoryData = record.getString("inventory_data");
        if (inventoryData == null || inventoryData.length() > MAX_BACKUP_JSON_CHARS) {
            admin.sendMessage(Messages.text("backup-gui-listener.dane-ekwipunku-backupu-sa-puste-lub-zbyt"));
            return;
        }
        UUID victimId;
        try {
            victimId = UUID.fromString(victimUuid);
        } catch (RuntimeException exception) {
            admin.sendMessage(Messages.text("backup-gui-listener.backup-zawiera-nieprawidlowy-uuid-gracza"));
            return;
        }
        InventorySerializer.PreparedInventory preparedInventory;
        try {
            preparedInventory = InventorySerializer.prepareInventory(inventoryData);
        } catch (RuntimeException malformedInventory) {
            admin.sendMessage(Messages.text("backup-gui-listener.dane-ekwipunku-backupu-sa-uszkodzone-backup-nie"));
            plugin.getLogger().warning("Refusing malformed death backup " + recordId + ": "
                    + malformedInventory.getMessage());
            return;
        }
        UUID adminUuid = admin.getUniqueId();
        String adminName = admin.getName();
        String claimToken = UUID.randomUUID().toString();
        Player victim = Bukkit.getPlayer(victimId);
        Integer refund = record.get("refundableRating") instanceof Integer points && points >= 0 && points <= 50
                ? points : null;
        if (restoreStats && (refund == null || victim == null || plugin.getUserManager().getUser(victim) == null
                || plugin.getUserManager().isTransferPending(victim))) {
            admin.sendMessage(refund == null ? Messages.text("backup-gui-listener.ten-backup-nie-ma-zapisanych-statystyk-do")
                    : Messages.text("backup-gui-listener.aby-zwrocic-statystyki-gracz-musi-byc-na"));
            return;
        }

        if (victim == null || !victim.isOnline()) {
            saveOfflineBackup(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
            return;
        }

        markRestoredAsync(recordId, adminName, claimToken).whenComplete((result, error) -> {
                boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentAdmin = Bukkit.getPlayer(adminUuid);
                    if (error != null) {
                        messageIfOnline(currentAdmin, Messages.text("backup-gui-listener.nie-udalo-sie-oznaczyc-backupu-jako-przywroconego"));
                        return;
                    }
                    if (result.getModifiedCount() == 0) {
                        messageIfOnline(currentAdmin, Messages.text("backup-gui-listener.ten-ekwipunek-zostal-juz-przywrocony-przez-innego"));
                        return;
                    }

                    Player currentVictim = Bukkit.getPlayer(victimId);
                    if (restoreStats && (currentVictim != victim || !victim.isOnline()
                            || plugin.getUserManager().getUser(victim) == null)) {
                        rollbackRestoreMarker(recordId, adminName, claimToken);
                        messageIfOnline(currentAdmin, Messages.text("backup-gui-listener.gracz-zmienil-sektor-lub-wyszedl-backup-pozostaje"));
                        return;
                    }
                    if (currentVictim == null || !currentVictim.isOnline()) {
                        savePendingAfterRestore(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
                        return;
                    }
                    if (plugin.getUserManager().isTransferPending(currentVictim)) {
                        rollbackRestoreMarker(recordId, adminName, claimToken);
                        messageIfOnline(currentAdmin, Messages.text("backup-gui-listener.gracz-jest-w-trakcie-transferu-ponow-przywrocenie"));
                        return;
                    }
                    try {
                        preparedInventory.applyTo(currentVictim.getInventory());
                    } catch (RuntimeException applyError) {
                        plugin.getLogger().severe("Backup apply failed after durable claim " + recordId
                                + "; manual review required: " + applyError.getMessage());
                        messageIfOnline(currentAdmin, Messages.text("backup-gui-listener.nie-udalo-sie-potwierdzic-przywrocenia-wymagana-reczna"));
                        return;
                    }
                    currentVictim.sendMessage(Messages.text("backup-gui-listener.twoj-ekwipunek-ze-smierci-zostal-przywrocony-przez"));
                    if (restoreStats) {
                        var users = plugin.getUserManager();
                        var profile = users.getUser(currentVictim);
                        int previous = profile.getRating();
                        profile.setRating((int) Math.min(ai.aisector.ranking.Rating.MAX, (long) previous + refund));
                        profile.setDeaths(Math.max(0, profile.getDeaths() - 1));
                        int returned = profile.getRating() - previous;
                        users.saveCombatStats(currentVictim, profile).whenComplete((ignored, statsError) ->
                                BukkitTaskScheduler.execute(plugin, () -> {
                                    Player operator = Bukkit.getPlayer(adminUuid);
                                    if (statsError != null) {
                                        messageIfOnline(operator, Messages.text("backup-gui-listener.oddano-ekwipunek-ale-zapis-statystyk-nie-zostal"));
                                    } else {
                                        messageIfOnline(operator, Messages.text("backup-gui-listener.zwrocono-pkt-i-skorygowano-smierc", returned));
                                        if (Bukkit.getPlayer(victimId) == currentVictim)
                                            currentVictim.sendMessage(Messages.text("backup-gui-listener.przywrocono-statystyki-z-backupu-pkt-1-smierc", returned));
                                    }
                                }));
                    }
                    finishRestore(currentAdmin, targetName);
                });
                if (!scheduled && error == null && result != null && result.getModifiedCount() > 0) {
                    if (restoreStats) rollbackRestoreMarker(recordId, adminName, claimToken);
                    else savePendingAfterRestore(adminUuid, adminName, claimToken, recordId, targetName, victimId, inventoryData);
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
                                    ? Messages.text("backup-gui-listener.gracz-ma-juz-oczekujacy-backup-obecny-backup-2")
                                    : Messages.text("backup-gui-listener.nie-mozna-potwierdzic-zapisu-oczekujacego-backupu-marker"));
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
                                        ? Messages.text("backup-gui-listener.gracz-ma-juz-oczekujacy-backup-obecny-backup")
                                        : Messages.text("backup-gui-listener.nie-udalo-sie-zakonczyc-przywracania-backupu-sprawdz"));
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
        admin.sendMessage(Messages.text("backup-gui-listener.pomyslnie-przywrocono-backup-dla-gracza", targetName));
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
            String targetName = ((PluginMenu) event.getView().getTopInventory().getHolder()).target();
            admin.performCommand("backup " + targetName);
        }
    }
}
