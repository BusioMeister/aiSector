// ServerCommand.java
package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import org.bukkit.command.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import ai.aisector.utils.BukkitTaskScheduler;

public class ServerCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public ServerCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.server.maintenance")) {
            sender.sendMessage("§cBrak uprawnień."); return true;
        }
        if (args.length < 2 || !"przerwa".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§eUżycie: /vserver przerwa <on|off>"); return true;
        }
        String sub = args[1].toLowerCase();
        if (!sub.equals("on") && !sub.equals("off")) {
            sender.sendMessage("§eUżycie: /vserver przerwa <on|off>"); return true;
        }
        String by = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";
        JSONObject req = new JSONObject();
        req.put("action", sub);
        req.put("by", by);
        req.put("countdown", 30); // sekundy
        String request = req.toString();
        plugin.getRedisManager().publishAsync(RedisChannels.MAINTENANCE_TOGGLE, request)
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage("§cBłąd komunikacji z proxy.");
                    } else {
                        sender.sendMessage("§aTryb przerwy: " + sub.toUpperCase(java.util.Locale.ROOT));
                    }
                }));
        return true;
    }
}
