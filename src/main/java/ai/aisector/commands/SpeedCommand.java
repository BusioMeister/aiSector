package ai.aisector.commands;
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
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /speed <1-10>");
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage("§cWystąpił błąd wczytywania Twoich danych.");
            return true;
        }
        try {
            int speedLevel = Integer.parseInt(args[0]);
            if (speedLevel < 0 || speedLevel > 10) { // Pozwalamy na 0, aby zresetować
                player.sendMessage("§cPrędkość musi być liczbą od 0 do 10.");
                return true;
            }
            if (player.isFlying()) {
                float speedValue = (speedLevel == 0) ? 0.1f : speedLevel / 10.0f;
                user.setFlySpeed(speedValue);
                player.setFlySpeed(speedValue);
                player.sendMessage("§aUstawiono prędkość latania na §e" + speedLevel + "§a.");
            } else {
                float speedValue = (speedLevel == 0) ? 0.2f : Math.min(1.0f, 0.2f * speedLevel);
                user.setWalkSpeed(speedValue);
                player.setWalkSpeed(speedValue);
                player.sendMessage("§aUstawiono prędkość chodzenia na §e" + speedLevel + "§a.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cPodana wartość nie jest liczbą!");
        }
        return true;
    }
}