package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ai.aisector.utils.BukkitTaskScheduler;

public class SendCommand implements CommandExecutor {

    private final SectorPlugin plugin;

    public SendCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.send")) {
            sender.sendMessage(Messages.text("send-command.nie-masz-uprawnien"));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Messages.text("send-command.uzycie-send"));
            return true;
        }

        String targetName = args[0];
        String targetSector = args[1];
        if (targetName.isBlank() || targetName.length() > 16 || targetSector.isBlank() || targetSector.length() > 128) {
            sender.sendMessage(Messages.text("send-command.nieprawidlowa-nazwa-gracza-lub-sektora"));
            return true;
        }
        if (Bukkit.getPlayer(targetName) == null) {
            sender.sendMessage(Messages.text("send-command.gracz-o-nicku-nie-jest-online-na", targetName));
        }

        // Krok B: Wysyłamy prośbę do Velocity o zorganizowanie transferu.
        JsonObject request = new JsonObject();
        request.addProperty("requesterName", sender.getName());
        request.addProperty("targetName", targetName);
        request.addProperty("targetSector", targetSector);

        sender.sendMessage(Messages.text("send-command.przetwarzanie-prosby-o-wyslanie-gracza"));
        plugin.getRedisManager().publishAsync(RedisChannels.SEND_REQUEST, request.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage(Messages.text("send-command.nie-udalo-sie-wyslac-zadania-transferu-do"));
                    }
                }));
        return true;
    }
}
