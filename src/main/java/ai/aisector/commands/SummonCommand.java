package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.user.UserManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ai.aisector.utils.BukkitTaskScheduler;

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
        if (targetName.isBlank() || targetName.length() > 16) {
            admin.sendMessage("§cNieprawidłowa nazwa gracza.");
            return true;
        }

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
        request.addProperty("operator", admin.isOp());
        request.addProperty("adminServer", plugin.getConfig().getString("this-sector-name", ""));

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

        String adminUuid = admin.getUniqueId().toString();
        String payload = request.toString();
        plugin.getRedisManager().publishAsync(RedisChannels.SUMMON_REQUEST, payload)
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    Player onlineAdmin = Bukkit.getPlayer(java.util.UUID.fromString(adminUuid));
                    if (onlineAdmin == null) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        onlineAdmin.sendMessage("§cNie udało się wysłać prośby o przywołanie gracza.");
                    } else {
                        onlineAdmin.sendMessage("§7Przetwarzanie prośby o przywołanie gracza...");
                    }
                }));
        return true;
    }
}
