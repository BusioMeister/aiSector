package ai.aisector.player;

import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.utils.InventorySerializer;
import ai.aisector.utils.MessageUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    private final Gson gson = new Gson();

    public PlayerJoinListener(JavaPlugin plugin, SectorManager sectorManager, RedisManager redisManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try (Jedis jedis = redisManager.getJedis()) {
            // KROK 1: ZAWSZE próbuj wczytać dane gracza (ekwipunek, zdrowie etc.)
            String playerDataKey = "player:data:" + player.getUniqueId();
            String playerData = jedis.get(playerDataKey);
            boolean dataLoaded = false;
            if (playerData != null) {
                jedis.del(playerDataKey);
                PlayerDataSerializer.deserialize(player, playerData);
                dataLoaded = true;
            }
            String tpposKey = "player:tppos_target:" + player.getUniqueId();
            String coordsJson = jedis.get(tpposKey);
            if (coordsJson != null) {
                jedis.del(tpposKey); // Usuwamy klucz po użyciu

                JsonObject coords = gson.fromJson(coordsJson, JsonObject.class);
                World world = Bukkit.getWorld(coords.get("world").getAsString());
                if (world != null) {
                    Location targetLocation = new Location(
                            world,
                            coords.get("x").getAsDouble(),
                            coords.get("y").getAsDouble(),
                            coords.get("z").getAsDouble()
                    );

                    // Teleportujemy gracza z małym opóźnieniem dla pewności
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(targetLocation);
                        player.sendMessage("§aPomyślnie przeniesiono i przeteleportowano na nowe koordynaty!");
                        sendWelcomePackage(player);
                    }, 5L);
                }
                return; // Zakończ dalsze przetwarzanie
            }

            // KROK 2: Obsługa /send
            String forceSpawnKey = "player:force_spawn:" + player.getUniqueId();
            if (jedis.exists(forceSpawnKey)) {
                jedis.del(forceSpawnKey);
                teleportToSectorSpawn(player);
                return;
            }

            // KROK 3: Obsługa teleportacji do konkretnej lokalizacji (/s, /tpa)
            String teleportLocationKey = "player:teleport_location:" + player.getUniqueId();
            String locationJson = jedis.get(teleportLocationKey);
            if (locationJson != null) {
                jedis.del(teleportLocationKey);
                JsonObject locData = gson.fromJson(locationJson, JsonObject.class);
                World world = Bukkit.getWorld(locData.get("world").getAsString());
                if (world != null) {
                    Location targetLocation = new Location(world, locData.get("x").getAsDouble(), locData.get("y").getAsDouble(), locData.get("z").getAsDouble(), locData.get("yaw").getAsFloat(), locData.get("pitch").getAsFloat());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(targetLocation);
                        player.sendMessage("§aZostałeś pomyślnie przeteleportowany.");
                        sendWelcomePackage(player);
                    }, 5L); // Zwiększone opóźnienie
                }
                return;
            }

            // KROK 4: Obsługa teleportacji do gracza (/tp)
            String tpTargetKey = "player:tp_target_uuid:" + player.getUniqueId();
            String targetUuidStr = jedis.get(tpTargetKey);
            if (targetUuidStr != null) {
                jedis.del(tpTargetKey);
                UUID targetUuid = UUID.fromString(targetUuidStr);
                // 🔥 ZMIANA: Zwiększamy opóźnienie do 5 ticków (1/4 sekundy)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player targetPlayer = Bukkit.getPlayer(targetUuid);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        player.teleport(targetPlayer.getLocation());
                        player.sendMessage("§aPomyślnie przeteleportowano do §e" + targetPlayer.getName());
                        sendWelcomePackage(player);
                    } else {
                        player.sendMessage("§cCel teleportacji jest już niedostępny.");
                        sendWelcomePackage(player);
                    }
                }, 5L);
                return;
            }
            String backupKey = "player:pending_backup:" + player.getUniqueId();
            String backupData = jedis.get(backupKey);
            if (backupData != null) {
                jedis.del(backupKey);
                InventorySerializer.deserializeAndUpdateInventory(player.getInventory(), backupData);
                player.sendMessage("§aTwój ekwipunek ze śmierci został przywrócony przez administratora.");
            }

            // KROK 5: Obsługa respawn lub /spawn
            if (jedis.exists("player:respawn:" + player.getUniqueId()) || jedis.exists("player:spawn_teleport:" + player.getUniqueId())) {
                jedis.del("player:respawn:" + player.getUniqueId());
                jedis.del("player:spawn_teleport:" + player.getUniqueId());
                teleportToGlobalSpawn(player, jedis);
                return;
            }

            // KROK 6: Jeśli dane zostały wczytane (normalny transfer), wyślij pakiet powitalny
            if (dataLoaded) {
                sendWelcomePackage(player);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas przetwarzania dołączenia gracza: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Wklej tutaj pełną zawartość metod pomocniczych z poprzedniej odpowiedzi, jeśli ich nie masz
    private void teleportToGlobalSpawn(Player player, Jedis jedis) {
        String spawnDataJson = jedis.get("aisector:global_spawn");
        if (spawnDataJson != null && !spawnDataJson.isEmpty()) {
            JsonObject spawnData = gson.fromJson(spawnDataJson, JsonObject.class);
            World world = Bukkit.getWorlds().get(0);
            Location spawnLocation = new Location(world,
                    spawnData.get("x").getAsDouble(),
                    spawnData.get("y").getAsDouble(),
                    spawnData.get("z").getAsDouble(),
                    spawnData.get("yaw").getAsFloat(),
                    spawnData.get("pitch").getAsFloat()
            );
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.teleport(spawnLocation);
                sendWelcomePackage(player);
            }, 1L);
        }
    }
    private void teleportToSectorSpawn(Player player) {
        String thisSectorName = plugin.getConfig().getString("this-sector-name");
        Sector currentSector = sectorManager.getSectorByName(thisSectorName);
        if (currentSector != null) {
            Location spawn = sectorManager.getSectorSpawnLocation(currentSector);
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
        sendWelcomePackage(player);
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