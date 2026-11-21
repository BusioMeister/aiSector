package ai.aisector.commands;

import ai.aisector.utils.Messages;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public class ClearInventoryCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("clear-inventory-command.ta-komenda-musi-byc-wykonana-przez-gracza"));
            return true;
        }

        Player player = (Player) sender;

        // Uprawnienie tylko dla adminów
        if (!player.hasPermission("aisector.command.ci")) {
            player.sendMessage(Messages.text("clear-inventory-command.nie-masz-uprawnien-do-tej-komendy"));
            return true;
        }

        // Czyszczenie ekwipunku, zbroi i drugiej ręki
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);

        player.sendMessage(Messages.text("clear-inventory-command.twoj-ekwipunek-zostal-wyczyszczony"));
        return true;
    }
}