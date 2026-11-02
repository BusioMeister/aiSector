package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SektorCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    public SektorCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

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
        if (args[0].isBlank() || args[0].length() > 16) {
            sender.sendMessage("§cNieprawidłowa nazwa gracza.");
            return true;
        }

        JsonObject req = new JsonObject();
        req.addProperty("requesterName", sender.getName());
        req.addProperty("targetName", args[0]);

        redisManager.publishAsync(RedisChannels.SECTOR_REQUEST, req.toString()).whenComplete((subscribers, error) -> {
            if (error != null || subscribers == null || subscribers == 0) {
                BukkitTaskScheduler.execute(plugin, () -> sender.sendMessage(
                        "§cNie udało się sprawdzić sektora gracza: proxy niedostępne."));
            }
        });
        return true;
    }
}
