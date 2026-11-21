package ai.aisector.commands; // Upewnij się, że pakiet jest poprawny

import ai.aisector.utils.Messages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderchestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("enderchest-command.ta-komenda-musi-byc-wykonana-przez-gracza"));
            return true;
        }

        Player player = (Player) sender;

        // --- PRZYPADEK 1: /ec ---
        // Gracz otwiera swój własny Enderchest
        if (args.length == 0) {
            // Sprawdzenie uprawnień dla otwierania własnego enderchestu
            if (!player.hasPermission("aisector.command.ec.self")) {
                player.sendMessage(Messages.text("enderchest-command.nie-masz-uprawnien-do-tej-komendy"));
                return true;
            }

            player.openInventory(player.getEnderChest());
            player.sendMessage(Messages.text("enderchest-command.otworzono-twoj-enderchest"));
            return true;
        }

        // --- PRZYPADEK 2: /ec <nick> ---
        // Admin otwiera Enderchest innego gracza
        if (args.length == 1) {
            // Sprawdzenie uprawnień dla otwierania enderchestów innych graczy
            if (!player.hasPermission("aisector.command.ec.other")) {
                player.sendMessage(Messages.text("enderchest-command.nie-masz-uprawnien-aby-otwierac-enderchesty-innych"));
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                player.sendMessage(Messages.text("enderchest-command.gracz-o-nicku-nie-jest-online", args[0]));
                return true;
            }

            player.openInventory(targetPlayer.getEnderChest());
            player.sendMessage(Messages.text("enderchest-command.otworzono-enderchest-gracza", targetPlayer.getName()));
            return true;
        }

        // Jeśli podano złą liczbę argumentów
        player.sendMessage(Messages.text("enderchest-command.uzycie-ec-nick"));
        return true;
    }
}