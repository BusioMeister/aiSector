package ai.aisector.player;

import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

import java.time.Duration;
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
        UUID playerId = player.getUniqueId();
        String key = "player:data:" + playerId;
        try (Jedis jedis = redisManager.getJedis()) {
            String data = jedis.get(key);
            if (data != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    PlayerDataSerializer.deserialize(player, data);

                    Bukkit.getLogger().info("Player " + player.getName() + " joined at: " + player.getLocation());


                    String sectorName = sectorManager.getSectorForLocation(
                            player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ());

                    if (sectorName == null || sectorName.isEmpty()) {
                        plugin.getLogger().warning("Gracz " + player.getName() + " nie znajduje się w żadnym sektorze.");
                        return;
                    }

                    Sector sector = sectorManager.getSector(
                            player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ());

                    if (sector == null) {
                        plugin.getLogger().warning("Nie znaleziono sektora dla gracza " + player.getName());
                        return;
                    }

                    borderManager.sendWorldBorder(player, sector);

                    MessageUtil.sendTitle(player, "", "§7Zostałes §9połączony §7z sektorem §9" + sectorName, 300, 1000, 300);
                    MessageUtil.sendActionBar(player, "§7Aktualny sektor: §9" + sectorName);

                }, 2L);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas wczytywania danych gracza: " + e.getMessage());
        }
    }

}
