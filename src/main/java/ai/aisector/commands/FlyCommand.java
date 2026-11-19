package ai.aisector.commands;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.PlayerFlight;
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
        PlayerFlight.resolve(player.getGameMode(), user.isFlying(), user.isFlying()).apply(player);
        if (user.isFlying()) {
            player.sendMessage("§aLatanie zostało włączone.");
        } else {
            player.sendMessage("§cPrzełącznik /fly wyłączony. Tryb gry nadal może zezwalać na latanie.");
        }
        return true;
    }
}
