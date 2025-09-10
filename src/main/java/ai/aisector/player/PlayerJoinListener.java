package ai.aisector.player;

import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.utils.MessageUtil;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final WorldBorderManager borderManager;

    public PlayerJoinListener(JavaPlugin plugin, SectorManager sectorManager, RedisManager redisManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean needsWelcomePackage = true;

        try (Jedis jedis = redisManager.getJedis()) {

            String summonLocationJson = jedis.get("player:summon_location:" + player.getUniqueId());
            if (summonLocationJson != null) {
                // ... (logika dla /s bez zmian)
                return;
            }
            else if (jedis.exists("player:tp_target:" + player.getUniqueId())) {
                // ... (logika dla /tp bez zmian)
                return;
            }
            // 🔥 KLUCZOWA ZMIANA: Usunęliśmy warunek '|| !player.hasPlayedBefore()'
            else if (jedis.exists("player:respawn:" + player.getUniqueId()) || jedis.exists("player:spawn_teleport:" + player.getUniqueId())) {
                jedis.del("player:respawn:" + player.getUniqueId());
                jedis.del("player:spawn_teleport:" + player.getUniqueId());

                String spawnDataJson = jedis.get("aisector:global_spawn");
                if (spawnDataJson != null && !spawnDataJson.isEmpty()) {
                    Document spawnData = Document.parse(spawnDataJson);
                    World world = Bukkit.getWorlds().get(0);
                    Location spawnLocation = new Location(world, spawnData.getDouble("x"), spawnData.getDouble("y"), spawnData.getDouble("z"), spawnData.getDouble("yaw").floatValue(), spawnData.getDouble("pitch").floatValue());

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(spawnLocation);
                        sendWelcomePackage(player);
                    }, 1L);
                }
                return;
            }
            else {
                String playerData = jedis.get("player:data:" + player.getUniqueId());
                if (playerData != null) {
                    PlayerDataSerializer.deserialize(player, playerData);
                } else {
                    needsWelcomePackage = false;
                }
            }

            if (needsWelcomePackage) {
                sendWelcomePackage(player);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas przetwarzania dołączenia gracza: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendWelcomePackage(Player player) {
        if (!player.isOnline()) return;

        Sector sector = sectorManager.getSector(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (sector != null) {
            borderManager.sendWorldBorder(player, sector);
            MessageUtil.sendTitle(player, "", "§7Zostałeś §9połączony §7z sektorem §9" + sector.getName(), 300, 1000, 300);
            MessageUtil.sendActionBar(player, "§7Aktualny sektor: §9" + sector.getName());
        }
    }
}