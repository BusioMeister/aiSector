package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
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
import ai.aisector.utils.BukkitTaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HomeCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;

    public HomeCommand(SectorPlugin plugin, MongoDBManager mongoDBManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!player.hasPermission("aisector.command.home")) {
            player.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        mongoDBManager.findManyAsync("player_homes", Filters.eq("player_uuid", playerUuid.toString()))
                .whenComplete((homes, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(playerUuid);
                    if (currentPlayer != player || !player.isOnline()) return;
                    if (error != null) {
                        currentPlayer.sendMessage("§cNie udało się wczytać domów.");
                        plugin.getLogger().warning("Could not load homes for " + playerUuid + ": "
                                + error.getMessage());
                    } else if (homes == null || homes.isEmpty()) {
                        currentPlayer.sendMessage("§cNie masz jeszcze ustawionego żadnego domu! Użyj /sethome.");
                    } else {
                        openHomes(currentPlayer, homes);
                    }
                }));
        return true;
    }

    private void openHomes(Player player, List<Document> homes) {
        Inventory gui = Bukkit.createInventory(null, 9, "§8Wybierz dom do teleportacji");
        boolean[] rendered = new boolean[6];
        for (Document home : homes) {
            if (home == null) continue;
            try {
            Integer slot = home.getInteger("home_slot");
            Document location = home.get("location", Document.class);
            if (slot == null || slot < 1 || slot > 5 || rendered[slot] || location == null) continue;
            rendered[slot] = true;

            ItemStack homeItem = new ItemStack(Material.GREEN_CONCRETE);
            ItemMeta meta = homeItem.getItemMeta();
            meta.setDisplayName("§aDom #" + slot);
            List<String> lore = new ArrayList<>();
            String sector = home.getString("sector");
            lore.add("§7Sektor: §b" + (sector == null ? "?" : sector));
            lore.add("§7Koordynaty: §f" + intValue(location, "x") + ", "
                    + intValue(location, "y") + ", " + intValue(location, "z"));
            lore.add(" ");
            lore.add("§eKliknij, aby się przeteleportować.");
            meta.setLore(lore);
            homeItem.setItemMeta(meta);
            gui.setItem(slot + 1, homeItem);
            } catch (RuntimeException malformedHome) {
                plugin.getLogger().warning("Skipping malformed home entry for " + player.getUniqueId());
            }
        }
        player.openInventory(gui);
    }

    private int intValue(Document location, String key) {
        Number value = location.get(key, Number.class);
        return value == null ? 0 : value.intValue();
    }
}
