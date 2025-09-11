package ai.aisector.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class RepairCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }
        Player player = (Player) sender;

        // --- Obsługa /repair all ---
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("aisector.command.repair.all")) {
                player.sendMessage("§cNie masz uprawnień do naprawy całego ekwipunku.");
                return true;
            }

            int repairedItems = 0;
            // Pętla po całym ekwipunku, włącznie ze zbroją
            for (ItemStack item : player.getInventory().getContents()) {
                if (repairItem(item)) {
                    repairedItems++;
                }
            }
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (repairItem(armor)) {
                    repairedItems++;
                }
            }

            player.sendMessage("§aNaprawiono §e" + repairedItems + " §aprzedmiotów w Twoim ekwipunku.");
            return true;
        }

        // --- Obsługa /repair ---
        if (args.length == 0) {
            if (!player.hasPermission("aisector.command.repair.one")) {
                player.sendMessage("§cNie masz uprawnień do tej komendy.");
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType() == Material.AIR) {
                player.sendMessage("§cMusisz trzymać jakiś przedmiot w ręku!");
                return true;
            }

            if (repairItem(itemInHand)) {
                player.sendMessage("§aPrzedmiot w Twojej ręce został naprawiony.");
            } else {
                player.sendMessage("§cTego przedmiotu nie da się naprawić.");
            }
            return true;
        }

        player.sendMessage("§cUżycie: /repair [all]");
        return true;
    }

    /**
     * Pomocnicza metoda do naprawiania pojedynczego przedmiotu.
     * @param item Przedmiot do naprawy.
     * @return true, jeśli przedmiot został naprawiony, w przeciwnym razie false.
     */
    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        // Sprawdzamy, czy przedmiot ma wytrzymałość (może być uszkodzony)
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            if (damageable.hasDamage()) {
                damageable.setDamage(0);
                item.setItemMeta(meta);
                return true;
            }
        }
        return false;
    }
}