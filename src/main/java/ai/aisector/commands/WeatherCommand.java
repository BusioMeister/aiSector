package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.List;

public class WeatherCommand implements CommandExecutor {

    private final RedisManager redisManager;
    private final List<String> weatherTypes = Arrays.asList("clear", "rain", "thunder");

    public WeatherCommand(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.weather")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length != 1 || !weatherTypes.contains(args[0].toLowerCase())) {
            sender.sendMessage("§cUżycie: /weather <clear|rain|thunder>");
            return true;
        }

        String weather = args[0].toLowerCase();

        // Tworzymy wiadomość do wysłania
        JsonObject weatherUpdate = new JsonObject();
        weatherUpdate.addProperty("weatherType", weather);
        weatherUpdate.addProperty("admin", sender.getName()); // Dodajemy informację, kto zmienił pogodę

        // Publikujemy wiadomość na globalnym kanale Redis
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:global_weather_change", weatherUpdate.toString());
        }

        sender.sendMessage("§aRozesłano polecenie zmiany pogody na §e" + weather + " §ana wszystkich serwerach.");
        return true;
    }
}