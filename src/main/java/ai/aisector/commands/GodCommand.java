package ai.aisector.commands;

import ai.aisector.utils.Messages;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GodCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public GodCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.god")) {
            player.sendMessage(Messages.text("god-command.nie-masz-uprawnien"));
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("god-command.wystapil-blad-wczytywania-twoich-danych"));
            return true;
        }
        user.setGodMode(!user.isGodMode());
        player.setInvulnerable(user.isGodMode());
        player.sendMessage(user.isGodMode() ? Messages.text("god-command.tryb-god-zostal-wlaczony") : Messages.text("god-command.tryb-god-zostal-wylaczony"));
        return true;
    }
}