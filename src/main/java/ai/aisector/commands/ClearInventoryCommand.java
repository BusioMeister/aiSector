package ai.aisector.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public class ClearInventoryCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }

        Player player = (Player) sender;

        // Uprawnienie tylko dla adminów
        if (!player.hasPermission("aisector.command.ci")) {
            player.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        // Czyszczenie ekwipunku, zbroi i drugiej ręki
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);

        player.sendMessage("§aTwój ekwipunek został wyczyszczony.");
        return true;
    }
}