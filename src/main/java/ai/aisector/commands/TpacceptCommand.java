package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class TpacceptCommand implements CommandExecutor {
    private final RedisManager redisManager;
    public TpacceptCommand(RedisManager redisManager) { this.redisManager = redisManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        JsonObject req = new JsonObject();
        req.addProperty("accepter", sender.getName());
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:tpa_accept", req.toString());
        }
        return true;
    }
}