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

public class TimeCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final List<String> timeTypes = Arrays.asList("day", "night");

    public TimeCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.time")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("set")
                || !timeTypes.contains(args[1].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("§cUżycie: /time set <day|night>");
            return true;
        }

        String time = args[1].toLowerCase(Locale.ROOT);

        // Tworzymy wiadomość do wysłania
        JsonObject timeUpdate = new JsonObject();
        timeUpdate.addProperty("timeType", time);
        timeUpdate.addProperty("admin", sender.getName());

        // Publikujemy wiadomość na globalnym kanale Redis
        redisManager.publishAsync(RedisChannels.GLOBAL_TIME_CHANGE, timeUpdate.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage("§cNie udało się rozesłać zmiany czasu przez sieć.");
                    } else {
                        sender.sendMessage("§aRozesłano polecenie zmiany czasu na §e" + time
                                + " §ana wszystkich serwerach.");
                    }
                }));
        return true;
    }
}
