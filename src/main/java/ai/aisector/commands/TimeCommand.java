package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.List;

public class TimeCommand implements CommandExecutor {

    private final RedisManager redisManager;
    private final List<String> timeTypes = Arrays.asList("day", "night");

    public TimeCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.time")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("set") || !timeTypes.contains(args[1].toLowerCase())) {
            sender.sendMessage("§cUżycie: /time set <day|night>");
            return true;
        }

        String time = args[1].toLowerCase();

        // Tworzymy wiadomość do wysłania
        JsonObject timeUpdate = new JsonObject();
        timeUpdate.addProperty("timeType", time);
        timeUpdate.addProperty("admin", sender.getName());

        // Publikujemy wiadomość na globalnym kanale Redis
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:global_time_change", timeUpdate.toString());
        }

        sender.sendMessage("§aRozesłano polecenie zmiany czasu na §e" + time + " §ana wszystkich serwerach.");
        return true;
    }
}