package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.player.PlayerDataSerializer;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class TpposCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;

    public TpposCommand(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.tppos")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("§cUżycie: /tppos <x> <y> <z>");
            return true;
        }

        Player player = (Player) sender;
        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cWspółrzędne muszą być liczbami!");
            return true;
        }

        Location targetLocation = new Location(player.getWorld(), x, y, z);
        String targetSectorName = sectorManager.getSectorForLocation((int) x, (int) z);
        String currentSectorName = sectorManager.getSectorForLocation(player.getLocation().getBlockX(), player.getLocation().getBlockZ());

        if (targetSectorName == null || targetSectorName.isEmpty()) {
            player.sendMessage("§cPodane koordynaty nie należą do żadnego znanego sektora.");
            return true;
        }

        // PRZYPADEK 1: Gracz jest już na właściwym sektorze
        if (targetSectorName.equals(currentSectorName)) {
            player.teleport(targetLocation);
            player.sendMessage("§aPrzeteleportowano na koordynaty: §e" + (int)x + ", " + (int)y + ", " + (int)z);
            // Upewniamy się, że border jest poprawny
            Sector currentSector = sectorManager.getSector(targetLocation.getBlockX(), targetLocation.getBlockZ());
            if(currentSector != null) {
                borderManager.sendWorldBorder(player, currentSector);
            }
        }
        // PRZYPADEK 2: Gracz musi zostać przeniesiony na inny sektor
        else {
            try (Jedis jedis = redisManager.getJedis()) {
                player.sendMessage("§7Koordynaty znajdują się w innym sektorze. Rozpoczynam transfer...");

                // 1. Zapisujemy dane (ekwipunek, HP etc.) gracza
                String playerData = PlayerDataSerializer.serialize(player, player.getLocation());
                jedis.setex("player:data:" + player.getUniqueId(), 60, playerData);

                // 2. Zapisujemy docelowe koordynaty w Redis
                JsonObject coords = new JsonObject();
                coords.addProperty("world", targetLocation.getWorld().getName());
                coords.addProperty("x", x);
                coords.addProperty("y", y);
                coords.addProperty("z", z);
                jedis.setex("player:tppos_target:" + player.getUniqueId(), 60, coords.toString());

                // 3. Zlecamy transfer gracza
                sectorManager.transferPlayer(player.getUniqueId(), targetSectorName);
            }
        }
        return true;
    }
}