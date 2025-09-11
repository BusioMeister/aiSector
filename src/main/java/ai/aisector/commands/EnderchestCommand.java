package ai.aisector.commands; // Upewnij się, że pakiet jest poprawny

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderchestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }

        Player player = (Player) sender;

        // --- PRZYPADEK 1: /ec ---
        // Gracz otwiera swój własny Enderchest
        if (args.length == 0) {
            // Sprawdzenie uprawnień dla otwierania własnego enderchestu
            if (!player.hasPermission("aisector.command.ec.self")) {
                player.sendMessage("§cNie masz uprawnień do tej komendy.");
                return true;
            }

            player.openInventory(player.getEnderChest());
            player.sendMessage("§aOtworzono Twój Enderchest.");
            return true;
        }

        // --- PRZYPADEK 2: /ec <nick> ---
        // Admin otwiera Enderchest innego gracza
        if (args.length == 1) {
            // Sprawdzenie uprawnień dla otwierania enderchestów innych graczy
            if (!player.hasPermission("aisector.command.ec.other")) {
                player.sendMessage("§cNie masz uprawnień, aby otwierać Enderchesty innych graczy.");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                player.sendMessage("§cGracz o nicku '" + args[0] + "' nie jest online.");
                return true;
            }

            player.openInventory(targetPlayer.getEnderChest());
            player.sendMessage("§aOtworzono Enderchest gracza §e" + targetPlayer.getName() + "§a.");
            return true;
        }

        // Jeśli podano złą liczbę argumentów
        player.sendMessage("§cUżycie: /ec [nick]");
        return true;
    }
}