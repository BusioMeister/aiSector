package ai.aisector.player;

import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.utils.Direction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final SectorManager sectorManager;
    private final RedisManager redisManager; // Przywracamy RedisManager
    private final WorldBorderManager borderManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final double WARNING_DISTANCE = 20.0;

    // Aktualizujemy konstruktor
    public PlayerListener(SectorManager sectorManager, RedisManager redisManager, WorldBorderManager borderManager) {
        this.sectorManager = sectorManager;
        this.redisManager = redisManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        String previousSectorId = sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());

        // Logika bordera i bossbaru (bez zmian)
        Sector currentSector = sectorManager.getSector(to.getBlockX(), to.getBlockZ());
        if (currentSector != null) {
            // ... (logika bossbaru i bordera pozostaje taka sama)
            borderManager.sendWorldBorder(player, currentSector);
        }

        if (!previousSectorId.equals(newSectorId)) {
            if (newSectorId == null || newSectorId.isEmpty()) {
                player.teleport(from); // Cofnij gracza
                player.sendMessage("§cNie możesz przejść dalej — ten obszar nie należy do żadnego sektora.");
                event.setCancelled(true);
                return;
            }

            // 🔥 PRZYWRACAMY KLUCZOWĄ LOGIKĘ ZAPISU DANYCH
            try (Jedis jedis = redisManager.getJedis()) {
                String key = "player:data:" + player.getUniqueId();
                String data = PlayerDataSerializer.serialize(player, to); // Zapisujemy pozycję docelową
                jedis.setex(key, 30, data); // Ustawiamy z krótkim czasem wygaśnięcia (30 sekund)
            }

            Bukkit.getLogger().info("Gracz " + player.getName() + " przeszedł z sektora " + previousSectorId + " do sektora " + newSectorId);
            sectorManager.transferPlayer(player.getUniqueId(), newSectorId);
        }
    }
    private void showBossBar(Player player, double distance) {
        BossBar bar = bossBars.get(player.getUniqueId());
        String title = "§eGranica sektora za §c" + (int) distance + " §ekratek";
        double progress = Math.max(0, Math.min(1.0, distance / WARNING_DISTANCE));

        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_10);
            bossBars.put(player.getUniqueId(), bar);
            bar.addPlayer(player);
        }

        bar.setTitle(title);
        bar.setProgress(progress);
        bar.setVisible(true);
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
            bossBars.remove(player.getUniqueId());
        }
    }
}