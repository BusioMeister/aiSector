package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import com.google.gson.JsonObject;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SectorInfoCommand implements CommandExecutor {

    private final SectorPlugin plugin;

    public SectorInfoCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("sector-info-command.gui-jest-dostepne-tylko-dla-graczy"));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.sectorinfo")) {
            player.sendMessage(Messages.text("sector-info-command.nie-masz-uprawnien"));
            return true;
        }

        String uuid = player.getUniqueId().toString();
        JsonObject request = new JsonObject();
        request.addProperty("uuid", uuid);
        plugin.getRedisManager().publishAsync(RedisChannels.GUI_DATA_REQUEST, request.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        player.sendMessage(Messages.text("sector-info-command.nie-udalo-sie-pobrac-informacji-o-sektorach"));
                    }
                }));
        return true;
    }
}
