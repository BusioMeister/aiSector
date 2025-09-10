package ai.aisector.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.fly")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        // Przełącz latanie
        player.setAllowFlight(!player.getAllowFlight());

        if (player.getAllowFlight()) {
            player.sendMessage("§aLatanie zostało włączone.");
        } else {
            player.sendMessage("§cLatanie zostało wyłączone.");
        }
        return true;
    }
}