package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.redis.packet.RedisPacketPublisher;
import ai.aisector.shared.RedisChannels;
import ai.aisector.shared.packet.AlertPacket;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AlertCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;

    public AlertCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.alert")) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUżycie: /alert <wiadomość>");
            return true;
        }

        String message = String.join(" ", args);
        if (message.length() > 256) {
            sender.sendMessage("§cOgłoszenie może mieć maksymalnie 256 znaków.");
            return true;
        }
        // The alert is delivered as the registered shared packet on the canonical packet channel.
        String formattedMessage = "§8[§cOGŁOSZENIE§8] §f" + message;
        AlertPacket packet = new AlertPacket();
        packet.message = formattedMessage;

        new RedisPacketPublisher(redisManager).publishAsync(RedisChannels.PACKET, packet)
                .whenComplete((subscribers, error) -> {
            BukkitTaskScheduler.execute(plugin, () -> {
                if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
                if (error != null) {
                    sender.sendMessage("§cNie udało się wysłać ogłoszenia.");
                } else if (subscribers == null || subscribers == 0) {
                    sender.sendMessage("§eOgłoszenie nie dotarło do żadnego odbiorcy.");
                } else {
                    sender.sendMessage("§aOgłoszenie zostało wysłane do sieci.");
                }
            });
                });
        return true;
    }
}
