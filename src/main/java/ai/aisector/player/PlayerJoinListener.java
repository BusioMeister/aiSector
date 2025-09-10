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

        try (Jedis jedis = redisManager.getJedis()) {

            // --- KROK 1: Sprawdź, czy gracz ma być siłowo wysłany na spawn sektora (TYLKO przez /send) ---
            String forceSpawnKey = "player:force_spawn:" + player.getUniqueId();
            if (jedis.exists(forceSpawnKey)) {
                jedis.del(forceSpawnKey); // Usuń znacznik
                teleportToSectorSpawn(player);
                return; // Zakończ, akcja wykonana.
            }

            // --- KROK 2: Obsłuż specjalne akcje (tp, summon, respawn, spawn) ---
            if (handleSpecialJoins(player, jedis)) {
                return;
            }

            // --- KROK 3: Obsłuż normalny transfer między sektorami ---
            String playerDataKey = "player:data:" + player.getUniqueId();
            String playerData = jedis.get(playerDataKey);
            if (playerData != null) {
                jedis.del(playerDataKey); // Zawsze kasujemy "bilet" po użyciu
                PlayerDataSerializer.deserialize(player, playerData);
                sendWelcomePackage(player);
            }

            // 🔥 WAŻNA ZMIANA: Jeśli żaden z powyższych warunków nie jest spełniony
            // (czyli jest to zwykły relog), nie robimy NIC. Gracz pojawi się w swoim
            // ostatnim zapisanym miejscu, co jest poprawnym zachowaniem.

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas przetwarzania dołączenia gracza: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obsługuje specjalne przypadki dołączenia, takie jak /tp, /s, respawn.
     * @return true, jeśli wykonano specjalną akcję, w przeciwnym razie false.
     */
    private boolean handleSpecialJoins(Player player, Jedis jedis) {
        // --- Sprawdź, czy gracz został przywołany przez /s ---
        String summonLocationJson = jedis.get("player:summon_location:" + player.getUniqueId());
        if (summonLocationJson != null) {
            jedis.del("player:summon_location:" + player.getUniqueId());
            Document locationData = Document.parse(summonLocationJson);
            World world = Bukkit.getWorld(locationData.getString("world"));
            if (world != null) {
                Location summonLocation = new Location(world, locationData.getDouble("x"), locationData.getDouble("y"), locationData.getDouble("z"), locationData.getDouble("yaw").floatValue(), locationData.getDouble("pitch").floatValue());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(summonLocation);
                    player.sendMessage("§aZostałeś przywołany.");
                    sendWelcomePackage(player);
                }, 1L);
            }
            return true;
        }

        // --- Sprawdź, czy to teleportacja z /tp ---
        String targetUUIDString = jedis.get("player:tp_target:" + player.getUniqueId());
        if (targetUUIDString != null) {
            jedis.del("player:tp_target:" + player.getUniqueId());
            UUID targetUUID = UUID.fromString(targetUUIDString);
            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(targetPlayer.getLocation());
                    player.sendMessage("§aPomyślnie przeteleportowano do §e" + targetPlayer.getName());
                    sendWelcomePackage(player);
                }, 1L);
            } else {
                player.sendMessage("§cCel teleportacji wylogował się.");
            }
            return true;
        }

        // --- Sprawdź, czy to respawn lub /spawn ---
        if (jedis.exists("player:respawn:" + player.getUniqueId()) || jedis.exists("player:spawn_teleport:" + player.getUniqueId())) {
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
            return true;
        }

        return false;
    }

    /**
     * Teleportuje gracza na środek sektora, na którym się znajduje.
     */
    private void teleportToSectorSpawn(Player player) {
        String thisSectorName = plugin.getConfig().getString("this-sector-name");
        Sector currentSector = sectorManager.getSectorByName(thisSectorName);
        Location spawn = sectorManager.getSectorSpawnLocation(currentSector);
        if (spawn != null) {
            player.teleport(spawn);
        }
        sendWelcomePackage(player);
    }

    /**
     * Pomocnicza metoda do wysyłania bordera i wiadomości powitalnych.
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