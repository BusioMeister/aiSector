package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class VanishCommand implements CommandExecutor {

    private final RedisManager redisManager;

    public VanishCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player admin = (Player) sender;
        if (!admin.hasPermission("aisector.command.vanish")) {
            admin.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        try (Jedis jedis = redisManager.getJedis()) {
            String redisKey = "aisector:vanished_players";
            boolean isVanished = jedis.sismember(redisKey, admin.getUniqueId().toString());

            JsonObject updateData = new JsonObject();
            updateData.addProperty("uuid", admin.getUniqueId().toString());

            JsonObject chatData = new JsonObject();
            chatData.addProperty("adminName", admin.getName());

            if (isVanished) {
                // Wyłącz Vanish
                jedis.srem(redisKey, admin.getUniqueId().toString());
                updateData.addProperty("action", "UNVANISH");
                chatData.addProperty("message", "§c[Admin] " + admin.getName() + " wyłączył tryb Vanish.");
            } else {
                // Włącz Vanish
                jedis.sadd(redisKey, admin.getUniqueId().toString());
                updateData.addProperty("action", "VANISH");
                chatData.addProperty("message", "§a[Admin] " + admin.getName() + " włączył tryb Vanish.");
            }

            // Opublikuj informację dla innych serwerów
            jedis.publish("aisector:vanish_update", updateData.toString());
            jedis.publish("aisector:admin_chat", chatData.toString());
        }
        return true;
    }
}