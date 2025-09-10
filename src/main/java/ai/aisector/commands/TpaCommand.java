// TpaCommand.java
package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class TpaCommand implements CommandExecutor {
    private final RedisManager redisManager;
    public TpaCommand(RedisManager redisManager) { this.redisManager = redisManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /tpa <gracz>");
            return true;
        }
        JsonObject req = new JsonObject();
        req.addProperty("requester", sender.getName());
        req.addProperty("target", args[0]);
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:tpa_request", req.toString());
        }
        return true;
    }
}