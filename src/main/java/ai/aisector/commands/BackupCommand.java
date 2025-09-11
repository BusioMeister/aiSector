package ai.aisector.commands;

import ai.aisector.database.MongoDBManager;
import com.mongodb.client.FindIterable;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BackupCommand implements CommandExecutor {

    private final MongoDBManager mongoDBManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public BackupCommand(MongoDBManager mongoDBManager) {
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.backup")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /backup <gracz>");
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];

        FindIterable<Document> deathRecords = mongoDBManager.getCollection("death_backups")
                .find(Filters.eq("victim_name", targetName))
                .sort(Sorts.descending("timestamp"))
                .limit(36); // Zwiększamy limit do rozmiaru GUI

        List<Document> records = deathRecords.into(new ArrayList<>());

        if (records.isEmpty()) {
            admin.sendMessage("§cNie znaleziono żadnych zapisów śmierci dla gracza §e" + targetName + "§c.");
            return true;
        }

        Inventory gui = Bukkit.createInventory(null, 45, "§8Backupy gracza: §c" + targetName);

        // 🔥 NOWA LOGIKA: Ręcznie wstawiamy głowy i wypełniacze 🔥
        int slotIndex = 0;
        for (Document record : records) {
            if (slotIndex >= 45) break; // Zabezpieczenie na wypadek większej liczby wyników

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();

            meta.setOwningPlayer(Bukkit.getOfflinePlayer(record.getString("victim_name")));
            meta.setDisplayName("§cZgon z dnia: §f" + dateFormat.format(record.getDate("timestamp")));

            List<String> lore = new ArrayList<>();
            Document loc = record.get("location", Document.class);

            lore.add("§7Sektor: §b" + record.getString("sector"));
            lore.add("§7Zabójca: §e" + record.getString("killer_name"));
            lore.add("§7Pozycja: §f" + loc.getInteger("x") + ", " + loc.getInteger("y") + ", " + loc.getInteger("z"));
            lore.add(" ");

            String restoredBy = record.getString("restored_by");
            if (restoredBy.equals("Nikt")) {
                lore.add("§aPrzywrócono przez: §fNikt");
                lore.add(" ");
                lore.add("§eLPM - Przywróć ekwipunek");
                lore.add("§ePPM - Pokaż ekwipunek");
            } else {
                lore.add("§cPrzywrócono przez: §f" + restoredBy);
                Date restoredAt = record.getDate("restored_at");
                if (restoredAt != null) {
                    lore.add("§cData przywrócenia: §f" + dateFormat.format(restoredAt));
                }
                lore.add(" ");
                lore.add("§ePPM - Pokaż ekwipunek");
            }

            lore.add("§0ID:" + record.getObjectId("_id").toHexString());

            meta.setLore(lore);
            skull.setItemMeta(meta);
            gui.setItem(slotIndex, skull);
            slotIndex++;
        }

        // Tworzymy przedmiot-wypełniacz
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        // Wypełniamy pozostałe sloty
        for (int i = slotIndex; i < 45; i++) {
            gui.setItem(i, filler);
        }

        admin.openInventory(gui);
        return true;
    }
}