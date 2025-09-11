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

public class SetHomeCommand implements CommandExecutor {

    private final MongoDBManager mongoDBManager;

    public SetHomeCommand(MongoDBManager mongoDBManager) {
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.sethome")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        Player player = (Player) sender;
        Inventory gui = Bukkit.createInventory(null, 9, "§8Ustaw swój dom");

        // Pobieramy istniejące domy gracza
        FindIterable<Document> homes = mongoDBManager.getCollection("player_homes")
                .find(Filters.eq("player_uuid", player.getUniqueId().toString()));

        for (int i = 1; i <= 5; i++) {
            boolean homeExists = false;
            for (Document home : homes) {
                if (home.getInteger("home_slot") == i) {
                    homeExists = true;
                    break;
                }
            }

            if (homeExists) {
                ItemStack greenConcrete = new ItemStack(Material.GREEN_CONCRETE);
                ItemMeta meta = greenConcrete.getItemMeta();
                meta.setDisplayName("§aDom #" + i + " (Ustawiony)");
                List<String> lore = new ArrayList<>();
                lore.add("§7Kliknij PPM, aby usunąć ten dom.");
                meta.setLore(lore);
                greenConcrete.setItemMeta(meta);
                gui.setItem(i + 1, greenConcrete); // Sloty 2, 3, 4, 5, 6
            } else {
                ItemStack redConcrete = new ItemStack(Material.RED_CONCRETE);
                ItemMeta meta = redConcrete.getItemMeta();
                meta.setDisplayName("§cPusty slot na dom #" + i);
                List<String> lore = new ArrayList<>();
                lore.add("§7Kliknij PPM, aby ustawić dom w tym miejscu.");
                meta.setLore(lore);
                redConcrete.setItemMeta(meta);
                gui.setItem(i + 1, redConcrete);
            }
        }

        player.openInventory(gui);
        return true;
    }
}