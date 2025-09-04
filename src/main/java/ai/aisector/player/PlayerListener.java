package ai.aisector.player;

import ai.aisector.utils.Direction;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.database.RedisManager;
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

import java.util.*;

public class PlayerListener implements Listener {

    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private final WorldBorderManager borderManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private final double WARNING_DISTANCE = 20.0; // dystans, przy którym pojawia się pasek

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

        UUID playerId = player.getUniqueId();
        String previousSectorId = sectorManager.getSectorForLocation(from.getBlockX(), from.getBlockZ());
        String newSectorId = sectorManager.getSectorForLocation(to.getBlockX(), to.getBlockZ());

        // 🟥 BossBar: ostrzegaj jeśli zbliża się do granicy
        Sector currentSector = sectorManager.getSector(to.getBlockX(), to.getBlockZ());
        if (currentSector != null) {
            double distanceToEdge = currentSector.distanceToBorder(to.getBlockX(), to.getBlockZ());
            if (distanceToEdge <= WARNING_DISTANCE) {
                String facingDirection = Direction.cardinal(to.getYaw());
                Sector next = sectorManager.getNextSector(currentSector, facingDirection);

                if (next != null) {
                    // Pokaż pasek tylko jeśli za granicą jest sektor
                    showBossBar(player, distanceToEdge);
                } else {
                    removeBossBar(player);
                }
            } else {
                removeBossBar(player);
            }
        }

        // 🟩 Przejście między sektorami
        if (!previousSectorId.equals(newSectorId)) {

            if (newSectorId == null || newSectorId.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage("§cNie możesz przejść dalej — ten obszar nie należy do żadnego sektora.");
                return;
            }

            Location location = Direction.fromLocations(from, to).add(to.clone());

            long start = System.currentTimeMillis();

            try (Jedis jedis = redisManager.getJedis()) {
                String key = "player:data:" + playerId;
                String data = PlayerDataSerializer.serialize(player, location);
                jedis.set(key, data);
                jedis.expire(key, 60 * 5);
            }

            long end = System.currentTimeMillis();
            Bukkit.getLogger().info("Gracz " + player.getName() + " przeszedł z sektora " + previousSectorId +
                    " do sektora " + newSectorId + " (" + (end - start) + "ms)");

            sectorManager.transferPlayer(playerId, newSectorId);
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
