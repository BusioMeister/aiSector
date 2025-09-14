package ai.aisector.commands;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public VanishCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.vanish")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage("§cWystąpił błąd wczytywania Twoich danych.");
            return true;
        }
        user.setVanished(!user.isVanished());
        if (user.isVanished()) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                    onlinePlayer.hidePlayer(plugin, player);
                }
            }
            player.sendMessage("§aVanish został włączony.");
        } else {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.showPlayer(plugin, player);
            }
            player.sendMessage("§cVanish został wyłączony.");
        }
        return true;
    }
}