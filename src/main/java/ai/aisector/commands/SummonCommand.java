package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.user.UserManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class SummonCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final UserManager userManager;

    public SummonCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.s")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /s <gracz>");
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];

        // Przypadek 1: Gracz jest na tym samym serwerze (działa natychmiast)
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetPlayer.teleport(admin.getLocation());
            targetPlayer.sendMessage("§aZostałeś przywołany.");
            admin.sendMessage("§aPrzywołano gracza §e" + targetName + "§a.");
            return true;
        }

        // Przypadek 2: Gracz jest na innym serwerze. Wysyłamy prośbę do Velocity.
        JsonObject request = new JsonObject();
        request.addProperty("adminUUID", admin.getUniqueId().toString());
        request.addProperty("targetName", targetName);

        // Dodajemy pełną lokalizację admina do prośby
        Location adminLoc = admin.getLocation();
        JsonObject locationJson = new JsonObject();
        locationJson.addProperty("world", adminLoc.getWorld().getName());
        locationJson.addProperty("x", adminLoc.getX());
        locationJson.addProperty("y", adminLoc.getY());
        locationJson.addProperty("z", adminLoc.getZ());
        locationJson.addProperty("yaw", adminLoc.getYaw());
        locationJson.addProperty("pitch", adminLoc.getPitch());
        request.add("adminLocation", locationJson);

        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            jedis.publish("aisector:summon_request", request.toString());
        }
        admin.sendMessage("§7Przetwarzanie prośby o przywołanie gracza...");
        return true;
    }
}