package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class HomeTeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final Location startLocation;
    private final String targetSector;
    private final JsonObject targetLocationJson;
    private final int homeSlot;
    private final SectorManager sectorManager;
    private final RedisManager redisManager;
    private int timeLeft = 5; // 5 sekund
    private final BossBar bossBar;

    public HomeTeleportWarmupTask(Player player, String targetSector, JsonObject targetLocationJson, int homeSlot, SectorPlugin plugin) {
        this.player = player;
        this.startLocation = player.getLocation();
        this.targetSector = targetSector;
        this.targetLocationJson = targetLocationJson;
        this.homeSlot = homeSlot;
        this.sectorManager = plugin.getSectorManager();
        this.redisManager = plugin.getRedisManager();
        this.bossBar = org.bukkit.Bukkit.createBossBar("§aTeleportacja do domu za: §e" + timeLeft, BarColor.GREEN, BarStyle.SOLID);
    }

    public void start() {
        this.bossBar.addPlayer(player);
        this.runTaskTimer(SectorPlugin.getPlugin(SectorPlugin.class), 0L, 20L);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        // Sprawdzamy, czy gracz się nie poruszył
        if (player.getLocation().distanceSquared(startLocation) > 1.0) {
            player.sendMessage("§cTeleportacja anulowana! Ruszyłeś się.");
            this.cancel();
            return;
        }

        if (timeLeft <= 0) {
            this.cancel();
            teleportPlayer();
            return;
        }

        bossBar.setTitle("§aTeleportacja do domu za: §e" + timeLeft);
        bossBar.setProgress((double) timeLeft / 5.0);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cNie ruszaj się! §7Teleportacja za §e" + timeLeft + "s"));
        timeLeft--;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void teleportPlayer() {
        String currentSector = sectorManager.getSectorForLocation(player.getLocation().getBlockX(), player.getLocation().getBlockZ());

        if (targetSector.equals(currentSector)) {
            // Teleport lokalny
            Location target = new Location(
                    player.getWorld(),
                    targetLocationJson.get("x").getAsDouble(),
                    targetLocationJson.get("y").getAsDouble(),
                    targetLocationJson.get("z").getAsDouble(),
                    targetLocationJson.get("yaw").getAsFloat(),
                    targetLocationJson.get("pitch").getAsFloat());
            player.teleport(target);
            player.sendMessage("§aPrzeteleportowano do domu #" + homeSlot + "!");
        } else {
            // Transfer międzysektorowy
            player.sendMessage("§7Ukończono odliczanie, rozpoczynam transfer...");
            try (Jedis jedis = redisManager.getJedis()) {
                // Zapisujemy ekwipunek i cel
                String invData = PlayerDataSerializer.serialize(player, player.getLocation());
                jedis.setex("player:data:" + player.getUniqueId(), 60, invData);
                jedis.setex("player:home_teleport_target:" + player.getUniqueId(), 60, targetLocationJson.toString());
                // Zlecamy transfer
                sectorManager.transferPlayer(player.getUniqueId(), targetSector);
            }
        }
    }
}