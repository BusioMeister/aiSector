package ai.aisector.commands;

import ai.aisector.utils.Messages;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpeedCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public SpeedCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.speed")) {
            player.sendMessage(Messages.text("speed-command.nie-masz-uprawnien"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Messages.text("speed-command.uzycie-speed"));
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("speed-command.wystapil-blad-wczytywania-twoich-danych"));
            return true;
        }
        try {
            int speedLevel = Integer.parseInt(args[0]);
            if (speedLevel < 0 || speedLevel > 10) { // Pozwalamy na 0, aby zresetować
                player.sendMessage(Messages.text("speed-command.predkosc-musi-byc-liczba-od-0-do"));
                return true;
            }
            if (player.isFlying()) {
                float speedValue = (speedLevel == 0) ? 0.1f : speedLevel / 10.0f;
                user.setFlySpeed(speedValue);
                player.setFlySpeed(speedValue);
                player.sendMessage(Messages.text("speed-command.ustawiono-predkosc-latania-na", speedLevel));
            } else {
                float speedValue = (speedLevel == 0) ? 0.2f : Math.min(1.0f, 0.2f * speedLevel);
                user.setWalkSpeed(speedValue);
                player.setWalkSpeed(speedValue);
                player.sendMessage(Messages.text("speed-command.ustawiono-predkosc-chodzenia-na", speedLevel));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.text("speed-command.podana-wartosc-nie-jest-liczba"));
        }
        return true;
    }
}