package ai.aisector.commands;

import ai.aisector.sectors.SectorManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class SectorInfoCommand implements Listener,CommandExecutor {
    private final SectorManager sectorManager;

    public SectorInfoCommand(SectorManager sectorManager) {
        this.sectorManager = sectorManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Tę komendę mogą używać tylko gracze.");
            return true;
        }

        Player player = (Player) sender;
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();

        String sectorId = sectorManager.getSectorForLocation(x, z);
        if (sectorId != null &&!sectorId.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "Znajdujesz się w sektorze: " + sectorId);
        } else {
            player.sendMessage(ChatColor.RED + "Nie znajdujesz się w żadnym zdefiniowanym sektorze.");
        }

        return true;
    }
}
