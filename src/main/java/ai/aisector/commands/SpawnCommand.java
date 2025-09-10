package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
// WAŻNY IMPORT: Potrzebujemy dostępu do serializera
import ai.aisector.player.PlayerDataSerializer;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class SpawnCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;

    public SpawnCommand(SectorPlugin plugin, RedisManager redisManager, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda może być wykonana tylko przez gracza.");
            return true;
        }

        Player player = (Player) sender;

        try (Jedis jedis = redisManager.getJedis()) {
            String spawnDataJson = jedis.get("aisector:global_spawn");

            if (spawnDataJson == null || spawnDataJson.isEmpty()) {
                player.sendMessage("§cSpawn serwera nie został jeszcze ustawiony!");
                return true;
            }

            Document spawnData = Document.parse(spawnDataJson);
            String targetSector = spawnData.getString("sector");
            String currentSectorName = sectorManager.getSectorForLocation(player.getLocation().getBlockX(), player.getLocation().getBlockZ());

            if (targetSector.equals(currentSectorName)) {
                // PRZYPADEK 1: Gracz jest na sektorze spawnu -> TELEPORT LOKALNY
                World world = Bukkit.getWorlds().get(0);
                Location spawnLocation = new Location(world,
                        spawnData.getDouble("x"),
                        spawnData.getDouble("y"),
                        spawnData.getDouble("z"),
                        spawnData.getDouble("yaw").floatValue(),
                        spawnData.getDouble("pitch").floatValue());

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(spawnLocation);
                    player.sendMessage("§aZostałeś przeteleportowany na spawn!");

                    Sector sector = sectorManager.getSector(spawnLocation.getBlockX(), spawnLocation.getBlockZ());
                    if (sector != null) {
                        borderManager.sendWorldBorder(player, sector);
                    }
                }, 1L);

            } else {
                // PRZYPADEK 2: Gracz jest na innym sektorze -> Zapisz dane i zleć transfer
                player.sendMessage("§7Trwa transfer na serwer spawnu...");

                // 🔥 NOWA LOGIKA: Zapisujemy dane gracza PRZED transferem 🔥
                String playerData = PlayerDataSerializer.serialize(player, player.getLocation());
                jedis.setex("player:data:" + player.getUniqueId(), 60, playerData);
                // ----------------------------------------------------------------

                jedis.setex("player:spawn_teleport:" + player.getUniqueId(), 10, "true");
                sectorManager.transferPlayer(player.getUniqueId(), targetSector);
            }
        } catch (Exception e) {
            player.sendMessage("§cWystąpił błąd podczas próby teleportacji.");
            e.printStackTrace();
        }

        return true;
    }
}