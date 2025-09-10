package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class SektorCommand implements CommandExecutor {
    private final RedisManager redisManager;
    public SektorCommand(RedisManager redisManager) { this.redisManager = redisManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.sektor")) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /sektor <gracz>");
            return true;
        }

        JsonObject req = new JsonObject();
        req.addProperty("requesterName", sender.getName());
        req.addProperty("targetName", args[0]);

        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:sektor_request", req.toString());
        }
        return true;
    }
}