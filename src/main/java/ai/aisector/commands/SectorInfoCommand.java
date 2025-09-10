package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class SectorInfoCommand implements CommandExecutor {

    private final RedisManager redisManager;

    public SectorInfoCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("GUI jest dostępne tylko dla graczy.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.sectorinfo")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        // Wyślij prośbę o dane do Velocity
        try (Jedis jedis = redisManager.getJedis()) {
            JsonObject request = new JsonObject();
            request.addProperty("uuid", player.getUniqueId().toString());
            jedis.publish("aisector:gui_data_request", request.toString());
        }
        return true;
    }
}