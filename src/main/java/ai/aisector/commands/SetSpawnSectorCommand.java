package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class SetSpawnSectorCommand implements CommandExecutor {

    private final SectorManager sectorManager;
    private final RedisManager redisManager;

    public SetSpawnSectorCommand(SectorManager sectorManager, RedisManager redisManager) {
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda może być wykonana tylko przez gracza.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aisector.setspawn")) {
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

        try (Jedis jedis = redisManager.getJedis()) {
            jedis.set("aisector:global_spawn", spawnData.toJson());
        } catch (Exception e) {
            player.sendMessage("§cWystąpił błąd podczas zapisu spawnu do bazy danych Redis.");
            e.printStackTrace();
            return true;
        }

        player.sendMessage("§aPomyślnie ustawiono globalny spawn serwera w sektorze §e" + sectorName + "§a!");
        return true;
    }
}