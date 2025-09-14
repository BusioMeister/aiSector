package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import java.util.HashMap;
import java.util.Map;

public class SpawnCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final UserManager userManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public SpawnCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda może być wykonana tylko przez gracza.");
            return true;
        }

        Player player = (Player) sender;
        String spawnDataJson;
        try (Jedis jedis = redisManager.getJedis()) {
            spawnDataJson = jedis.get("aisector:global_spawn");
        }

        if (spawnDataJson == null || spawnDataJson.isEmpty()) {
            player.sendMessage("§cSpawn serwera nie został jeszcze ustawiony!");
            return true;
        }

        JsonObject spawnData = gson.fromJson(spawnDataJson, JsonObject.class);
        String targetSector = spawnData.get("sector").getAsString();
        String currentSectorName = sectorManager.getSectorForLocation(player.getLocation().getBlockX(), player.getLocation().getBlockZ());

        World world = Bukkit.getWorlds().get(0);
        Location spawnLocation = new Location(world,
                spawnData.get("x").getAsDouble(), spawnData.get("y").getAsDouble(), spawnData.get("z").getAsDouble(),
                spawnData.get("yaw").getAsFloat(), spawnData.get("pitch").getAsFloat());

        if (targetSector.equals(currentSectorName)) {
            player.teleport(spawnLocation);
            player.sendMessage("§aZostałeś przeteleportowany na spawn!");
        } else {
            player.sendMessage("§7Trwa transfer na serwer spawnu...");

            // 1. Zapisujemy dane (EQ, HP) do Redis za pomocą UserManager
            userManager.savePlayerDataForTransfer(player, player.getLocation());

            // 2. Zapisujemy OSTATECZNY cel teleportacji w Redis
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.setex("player:final_teleport_target:" + player.getUniqueId(), 60, locationToJson(spawnLocation));
            }

            // 3. Zlecamy transfer gracza
            sectorManager.transferPlayer(player.getUniqueId(), targetSector);
        }
        return true;
    }

    // Metoda pomocnicza do konwersji Location na JSON
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