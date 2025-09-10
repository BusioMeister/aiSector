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
        boolean isSpecialJoin = false; // Flaga do śledzenia, czy to specjalne dołączenie

        try (Jedis jedis = redisManager.getJedis()) {

            // --- Sprawdź, czy gracz został przywołany przez /s ---
            String summonLocationJson = jedis.get("player:summon_location:" + player.getUniqueId());
            if (summonLocationJson != null) {
                isSpecialJoin = true;
                jedis.del("player:summon_location:" + player.getUniqueId());
                Document locationData = Document.parse(summonLocationJson);
                World world = Bukkit.getWorld(locationData.getString("world"));

                if (world != null) {
                    Location summonLocation = new Location(world,
                            locationData.getDouble("x"),
                            locationData.getDouble("y"),
                            locationData.getDouble("z"),
                            locationData.getDouble("yaw").floatValue(),
                            locationData.getDouble("pitch").floatValue());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(summonLocation);
                        player.sendMessage("§aZostałeś przywołany.");
                    }, 1L);
                }
            }
            // --- Sprawdź, czy to teleportacja z /tp ---
            else if (jedis.exists("player:tp_target:" + player.getUniqueId())) {
                isSpecialJoin = true;
                String targetUUIDString = jedis.get("player:tp_target:" + player.getUniqueId());
                jedis.del("player:tp_target:" + player.getUniqueId());
                UUID targetUUID = UUID.fromString(targetUUIDString);
                Player targetPlayer = Bukkit.getPlayer(targetUUID);

                if (targetPlayer != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(targetPlayer.getLocation());
                        player.sendMessage("§aPomyślnie przeteleportowano do §e" + targetPlayer.getName());
                    }, 1L);
                } else {
                    player.sendMessage("§cCel teleportacji wylogował się.");
                }
            }
            // --- Sprawdź, czy to respawn lub /spawn ---
            else if (jedis.exists("player:respawn:" + player.getUniqueId()) || jedis.exists("player:spawn_teleport:" + player.getUniqueId())) {
                isSpecialJoin = true;
                jedis.del("player:respawn:" + player.getUniqueId());
                jedis.del("player:spawn_teleport:" + player.getUniqueId());

                String spawnDataJson = jedis.get("aisector:global_spawn");
                if (spawnDataJson != null && !spawnDataJson.isEmpty()) {
                    Document spawnData = Document.parse(spawnDataJson);
                    World world = Bukkit.getWorlds().get(0);
                    Location spawnLocation = new Location(world, spawnData.getDouble("x"), spawnData.getDouble("y"), spawnData.getDouble("z"), spawnData.getDouble("yaw").floatValue(), spawnData.getDouble("pitch").floatValue());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(spawnLocation), 1L);
                }
            }
            // --- Jeśli nic z powyższych, to normalny transfer między sektorami ---
            else {
                String playerData = jedis.get("player:data:" + player.getUniqueId());
                if (playerData != null) {
                    // W tym przypadku deserializer ustawi pozycję, więc pakiet powitalny wyślemy od razu
                    PlayerDataSerializer.deserialize(player, playerData);
                    sendWelcomePackage(player);
                }
            }

            // Jeśli to było specjalne dołączenie (tp, s, spawn), wyślij border i tytuł po chwili,
            // aby dać czas na wykonanie teleportacji.
            if (isSpecialJoin) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> sendWelcomePackage(player), 5L);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas przetwarzania dołączenia gracza: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Pomocnicza metoda do wysyłania bordera i wiadomości powitalnych.
     * @param player Gracz, do którego wysyłamy pakiet.
     */
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