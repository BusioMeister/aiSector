package ai.aisector.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpeedCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.speed")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUżycie: /speed <1-10>");
            return true;
        }

        try {
            int speed = Integer.parseInt(args[0]);
            if (speed < 1 || speed > 10) {
                player.sendMessage("§cPrędkość musi być liczbą od 1 do 10.");
                return true;
            }

            // Przeliczamy prędkość na skalę Minecrafta (0.0 - 1.0)
            float finalSpeed = (float) speed / 10.0f;

            if (player.isFlying()) {
                player.setFlySpeed(finalSpeed);
                player.sendMessage("§aUstawiono prędkość latania na " + speed + ".");
            } else {
                player.setWalkSpeed(finalSpeed);
                player.sendMessage("§aUstawiono prędkość chodzenia na " + speed + ".");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cTo nie jest poprawna liczba.");
        }
        return true;
    }
}