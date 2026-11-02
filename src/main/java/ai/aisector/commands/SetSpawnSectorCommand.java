package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.SectorManager;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnSectorCommand implements CommandExecutor {

    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final SectorPlugin plugin;

    public SetSpawnSectorCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda może być wykonana tylko przez gracza.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.setspawn")) {
            player.sendMessage("§cNie masz uprawnień, aby użyć tej komendy.");
            return true;
        }

        Location spawnLocation = player.getLocation();
        String sectorName = sectorManager.getSectorForLocation(spawnLocation.getBlockX(), spawnLocation.getBlockZ());

        if (sectorName == null || sectorName.isEmpty()) {
            player.sendMessage("§cNie możesz ustawić spawnu, ponieważ nie stoisz w żadnym sektorze!");
            return true;
        }

        Document spawnData = new Document("sector", sectorName)
                .append("x", spawnLocation.getX())
                .append("y", spawnLocation.getY())
                .append("z", spawnLocation.getZ())
                .append("yaw", spawnLocation.getYaw())
                .append("pitch", spawnLocation.getPitch());
        java.util.UUID playerId = player.getUniqueId();

        redisManager.setAsync(RedisChannels.GLOBAL_SPAWN, spawnData.toJson())
                .whenComplete((result, error) -> {
                    BukkitTaskScheduler.execute(plugin, () -> {
                        if (!player.isOnline() || !player.getUniqueId().equals(playerId)
                                || Bukkit.getPlayer(playerId) != player) return;
                        if (error != null) {
                            player.sendMessage("§cWystąpił błąd podczas zapisu spawnu do Redis.");
                        } else {
                            player.sendMessage("§aPomyślnie ustawiono globalny spawn serwera w sektorze §e"
                                    + sectorName + "§a!");
                        }
                    });
                });
        return true;
    }
}
