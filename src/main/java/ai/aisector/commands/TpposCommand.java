package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;

public class TpposCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final UserManager userManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public TpposCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
        this.redisManager = plugin.getRedisManager();
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

        if (targetSectorName.equals(currentSectorName)) {
            player.teleport(targetLocation);
            player.sendMessage("§aPrzeteleportowano na koordynaty: §e" + (int) x + ", " + (int) y + ", " + (int) z);
        } else {
            player.sendMessage("§7Koordynaty znajdują się w innym sektorze. Rozpoczynam transfer...");

            userManager.savePlayerDataForTransfer(player, player.getLocation());

            try (Jedis jedis = redisManager.getJedis()) {
                // 🔥 POPRAWKA: Używamy 'targetLocation' zamiast nieistniejącej 'spawnLocation'
                jedis.setex("player:final_teleport_target:" + player.getUniqueId(), 60, locationToJson(targetLocation));
            }

            sectorManager.transferPlayer(player.getUniqueId(), targetSectorName);
        }
        return true;
    }

    private String locationToJson(Location loc) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        return gson.toJson(data);
    }
}