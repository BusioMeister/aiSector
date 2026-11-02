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

public class TpaCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    public TpaCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        if (!sender.hasPermission("aisector.command.tpa")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /tpa <gracz>");
            return true;
        }
        if (args[0].isBlank() || args[0].length() > 16) {
            sender.sendMessage("§cNieprawidłowa nazwa gracza.");
            return true;
        }
        JsonObject req = new JsonObject();
        req.addProperty("requester", sender.getName());
        req.addProperty("target", args[0]);
        redisManager.publishAsync(RedisChannels.TPA_REQUEST, req.toString()).whenComplete((subscribers, error) -> {
            if (error != null || subscribers == null || subscribers == 0) {
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    sender.sendMessage("§cNie udało się wysłać prośby TPA przez sieć.");
                });
            }
        });
        return true;
    }
}
