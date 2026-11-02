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
import ai.aisector.user.HomeSlotMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SetHomeCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;

    public SetHomeCommand(SectorPlugin plugin, MongoDBManager mongoDBManager) {
        this.plugin = plugin;
        this.mongoDBManager = mongoDBManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!player.hasPermission("aisector.command.sethome")) {
            player.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        mongoDBManager.findManyAsync("player_homes", Filters.eq("player_uuid", playerUuid.toString()))
                .whenComplete((homes, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(playerUuid);
                    if (currentPlayer != player || !player.isOnline()) return;
                    if (error != null) {
                        currentPlayer.sendMessage("§cNie udało się wczytać ustawień domów.");
                        plugin.getLogger().warning("Could not load home slots for " + playerUuid + ": "
                                + error.getMessage());
                        return;
                    }
                    openSetHomeGui(currentPlayer, homes == null ? List.of() : homes);
                }));
        return true;
    }

    private void openSetHomeGui(Player player, List<Document> homes) {
        HomeSlotMenu holder = new HomeSlotMenu(player.getUniqueId(), homes);
        Inventory gui = Bukkit.createInventory(holder, 9, "§8Ustaw swój dom");
        holder.attach(gui);
        boolean[] occupied = new boolean[6];
        for (Document home : homes) {
            if (home == null) continue;
            try {
            Integer slot = home.getInteger("home_slot");
            if (slot != null && slot >= 1 && slot <= 5) occupied[slot] = true;
            } catch (RuntimeException malformedHome) {
                plugin.getLogger().warning("Skipping malformed home slot for " + player.getUniqueId());
            }
        }

        for (int slot = 1; slot <= 5; slot++) {
            ItemStack item = new ItemStack(occupied[slot] ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(occupied[slot]
                    ? "§aDom #" + slot + " (Ustawiony)"
                    : "§cPusty slot na dom #" + slot);
            List<String> lore = new ArrayList<>();
            lore.add(occupied[slot] ? "§7Kliknij PPM, aby usunąć ten dom."
                    : "§7Kliknij PPM, aby ustawić dom w tym miejscu.");
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot + 1, item);
        }
        player.openInventory(gui);
    }
}
