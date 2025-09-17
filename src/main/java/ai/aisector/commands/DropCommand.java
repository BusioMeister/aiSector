package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.listeners.DropGui; // Będziemy potrzebować tego importu
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DropCommand implements CommandExecutor {

    private final SectorPlugin plugin;

    public DropCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("aisector.command.drop")) {
            player.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        // Tworzymy i otwieramy nowe GUI dla gracza
        new DropGui(plugin, player).open();
        return true;
    }
}