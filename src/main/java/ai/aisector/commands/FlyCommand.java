package ai.aisector.commands;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public FlyCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.fly")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage("§cWystąpił błąd wczytywania Twoich danych.");
            return true;
        }
        user.setFlying(!user.isFlying());
        player.setAllowFlight(user.isFlying());
        if (user.isFlying()) {
            player.setFlying(true);
            player.sendMessage("§aLatanie zostało włączone.");
        } else {
            player.setFlying(false);
            player.sendMessage("§cLatanie zostało wyłączone.");
        }
        return true;
    }
}