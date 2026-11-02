package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ai.aisector.utils.BukkitTaskScheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class BackupCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public BackupCommand(SectorPlugin plugin, MongoDBManager mongoDBManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!admin.hasPermission("aisector.command.backup")) {
            admin.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            admin.sendMessage("§cUżycie: /backup <gracz>");
            return true;
        }

        String targetName = args[0];
        if (targetName.isBlank() || targetName.length() > 16) {
            admin.sendMessage("§cNieprawidłowa nazwa gracza.");
            return true;
        }
        UUID adminUuid = admin.getUniqueId();
        mongoDBManager.findManyAsync("death_backups", Filters.eq("victim_name", targetName),
                        Sorts.descending("timestamp"), 36)
                .whenComplete((records, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentAdmin = Bukkit.getPlayer(adminUuid);
                    if (currentAdmin == null || !currentAdmin.isOnline()) return;
                    if (error != null) {
                        currentAdmin.sendMessage("§cNie udało się wczytać backupów.");
                        plugin.getLogger().warning("Could not load death backups for " + targetName + ": "
                                + error.getMessage());
                    } else if (records == null || records.isEmpty()) {
                        currentAdmin.sendMessage("§cNie znaleziono żadnych zapisów śmierci dla gracza §e"
                                + targetName + "§c.");
                    } else {
                        openBackupGui(currentAdmin, targetName, records);
                    }
                }));
        return true;
    }

    private void openBackupGui(Player admin, String targetName, List<Document> records) {
        Inventory gui = Bukkit.createInventory(null, 45, "§8Backupy gracza: §c" + targetName);
        int slotIndex = 0;
        for (Document record : records) {
            if (slotIndex >= 45) break;
            if (record == null || record.getObjectId("_id") == null) continue;
            try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            String victimUuid = record.getString("victim_uuid");
            if (victimUuid != null) {
                try {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(victimUuid)));
                } catch (IllegalArgumentException ignored) {
                    // Keep the generic head for legacy or malformed backup records.
                }
            }
            Date timestamp = record.getDate("timestamp");
            meta.setDisplayName("§cZgon z dnia: §f" + (timestamp == null ? "nieznana data" : dateFormat.format(timestamp)));

            List<String> lore = new ArrayList<>();
            Document location = record.get("location", Document.class);
            lore.add("§7Sektor: §b" + record.getString("sector"));
            lore.add("§7Zabójca: §e" + record.getString("killer_name"));
            if (location != null) {
                lore.add("§7Pozycja: §f" + coordinate(location, "x") + ", "
                        + coordinate(location, "y") + ", " + coordinate(location, "z"));
            }
            lore.add(" ");

            String restoredBy = record.getString("restored_by");
            if (restoredBy == null || restoredBy.equals("Nikt")) {
                lore.add("§aPrzywrócono przez: §fNikt");
                lore.add(" ");
                lore.add("§eLPM - Przywróć ekwipunek");
                lore.add("§ePPM - Pokaż ekwipunek");
            } else {
                lore.add("§cPrzywrócono przez: §f" + restoredBy);
                Date restoredAt = record.getDate("restored_at");
                if (restoredAt != null) lore.add("§cData przywrócenia: §f" + dateFormat.format(restoredAt));
                lore.add(" ");
                lore.add("§ePPM - Pokaż ekwipunek");
            }
            lore.add("§0ID:" + record.getObjectId("_id").toHexString());
            meta.setLore(lore);
            skull.setItemMeta(meta);
            gui.setItem(slotIndex++, skull);
            } catch (RuntimeException malformedRecord) {
                plugin.getLogger().warning("Skipping malformed death-backup record in GUI");
            }
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = slotIndex; i < 45; i++) gui.setItem(i, filler);
        admin.openInventory(gui);
    }

    private int coordinate(Document location, String axis) {
        Number value = location.get(axis, Number.class);
        return value == null ? 0 : value.intValue();
    }
}
