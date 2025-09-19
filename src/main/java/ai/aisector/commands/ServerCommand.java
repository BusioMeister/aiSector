// ServerCommand.java
package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

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
        try (Jedis j = plugin.getRedisManager().getJedis()) {
            JSONObject req = new JSONObject();
            req.put("action", sub);
            req.put("by", by);
            req.put("countdown", 30); // sekundy
            j.publish("aisector:maintenance_toggle", req.toString());
        } catch (Exception e) {
            sender.sendMessage("§cBłąd komunikacji z proxy.");
            return true;
        }
        sender.sendMessage("§aTryb przerwy: " + sub.toUpperCase());
        return true;
    }
}
