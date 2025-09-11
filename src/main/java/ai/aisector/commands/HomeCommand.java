package ai.aisector.commands;

import ai.aisector.database.MongoDBManager;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
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

import java.util.ArrayList;
import java.util.List;

public class HomeCommand implements CommandExecutor {

    private final MongoDBManager mongoDBManager;

    public HomeCommand(MongoDBManager mongoDBManager) {
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.home")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        Player player = (Player) sender;
        FindIterable<Document> homes = mongoDBManager.getCollection("player_homes")
                .find(Filters.eq("player_uuid", player.getUniqueId().toString()));

        if (!homes.iterator().hasNext()) {
            player.sendMessage("§cNie masz jeszcze ustawionego żadnego domu! Użyj /sethome.");
            return true;
        }

        Inventory gui = Bukkit.createInventory(null, 9, "§8Wybierz dom do teleportacji");

        for (Document home : homes) {
            int slot = home.getInteger("home_slot");
            Document loc = home.get("location", Document.class);

            ItemStack homeItem = new ItemStack(Material.GREEN_CONCRETE);
            ItemMeta meta = homeItem.getItemMeta();
            meta.setDisplayName("§aDom #" + slot);

            List<String> lore = new ArrayList<>();
            lore.add("§7Sektor: §b" + home.getString("sector"));

            // 🔥 TUTAJ JEST POPRAWKA: Zmieniamy getInteger na getDouble 🔥
            // Dodajemy (int) aby wyświetlić koordynaty jako liczby całkowite, bez przecinków.
            lore.add("§7Koordynaty: §f" + loc.getDouble("x").intValue() + ", " + loc.getDouble("y").intValue() + ", " + loc.getDouble("z").intValue());

            lore.add(" ");
            lore.add("§eKliknij, aby się przeteleportować.");
            meta.setLore(lore);
            homeItem.setItemMeta(meta);

            gui.setItem(slot + 1, homeItem);
        }

        player.openInventory(gui);
        return true;
    }
}