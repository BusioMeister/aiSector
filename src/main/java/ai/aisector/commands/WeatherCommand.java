package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class WeatherCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final List<String> weatherTypes = Arrays.asList("clear", "rain", "thunder");

    public WeatherCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.weather")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length != 1 || !weatherTypes.contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("§cUżycie: /weather <clear|rain|thunder>");
            return true;
        }

        String weather = args[0].toLowerCase(Locale.ROOT);

        // Tworzymy wiadomość do wysłania
        JsonObject weatherUpdate = new JsonObject();
        weatherUpdate.addProperty("weatherType", weather);
        weatherUpdate.addProperty("admin", sender.getName()); // Dodajemy informację, kto zmienił pogodę

        // Publikujemy wiadomość na globalnym kanale Redis
        redisManager.publishAsync(RedisChannels.GLOBAL_WEATHER_CHANGE, weatherUpdate.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage("§cNie udało się rozesłać zmiany pogody przez sieć.");
                    } else {
                        sender.sendMessage("§aRozesłano polecenie zmiany pogody na §e" + weather
                                + " §ana wszystkich serwerach.");
                    }
                }));
        return true;
    }
}
