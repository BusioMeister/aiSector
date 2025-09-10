package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import redis.clients.jedis.Jedis;

public class AlertCommand implements CommandExecutor {

    private final RedisManager redisManager;

    public AlertCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
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
        // Formatujemy wiadomość (możesz dostosować)
        String formattedMessage = "§8[§cOGŁOSZENIE§8] §f" + message;

        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:alert", formattedMessage);
        }
        return true;
    }
}