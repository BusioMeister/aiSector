package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import com.google.gson.JsonObject; // ZMIANA: Używamy JsonObject
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class TpCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;

    public TpCommand(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.tp")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /tp <gracz>");
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            // PRZYPADEK 1: Gracz jest lokalnie
            admin.sendMessage("§7Teleportuję do gracza §e" + targetName + "§7...");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                admin.teleport(targetPlayer.getLocation());
                Sector currentSector = sectorManager.getSector(targetPlayer.getLocation().getBlockX(), targetPlayer.getLocation().getBlockZ());
                if (currentSector != null) {
                    borderManager.sendWorldBorder(admin, currentSector);
                }
            }, 1L);
            return true;
        }

        // PRZYPADEK 2: Gracza nie ma lokalnie -> wyślij prośbę do Velocity
        // ZMIANA: Używamy spójnego formatu JsonObject
        JsonObject request = new JsonObject();
        request.addProperty("adminName", admin.getName());
        request.addProperty("targetName", targetName);

        try (Jedis jedis = redisManager.getJedis()) {
            jedis.publish("aisector:tp_request", request.toString());
        }
        sender.sendMessage("§7Przetwarzanie prośby o teleportację...");
        return true;
    }
}