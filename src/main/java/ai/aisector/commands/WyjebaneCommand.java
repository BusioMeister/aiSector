package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class WyjebaneCommand implements CommandExecutor {

    private final RedisManager redisManager;

    public WyjebaneCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        try (Jedis jedis = redisManager.getJedis()) {
            String key = "pm_disabled:" + player.getUniqueId();
            if (jedis.exists(key)) {
                // Wiadomości są wyłączone, więc je włączamy (usuwając klucz)
                jedis.del(key);
                player.sendMessage("§aOd teraz masz WYJEBANE!");
            } else {
                // Wiadomości są włączone, więc je wyłączamy (dodając klucz)
                jedis.set(key, "true");
                player.sendMessage("§cOd teraz §4NIE §cmasz WYJEBANE!");
            }
        }
        return true;
    }
}