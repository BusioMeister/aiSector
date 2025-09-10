package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import redis.clients.jedis.Jedis;

public class SendCommand implements CommandExecutor {
    private final RedisManager redisManager;
    public SendCommand(RedisManager redisManager) { this.redisManager = redisManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.send")) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUżycie: /send <gracz> <sektor>");
            return true;
        }

        JsonObject req = new JsonObject();
        req.addProperty("requesterName", sender.getName());
        req.addProperty("targetName", args[0]);
        req.addProperty("targetSector", args[1]);

        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:send_request", req.toString());
        }
        sender.sendMessage("§7Przetwarzanie prośby o przeniesienie gracza...");
        return true;
    }
}