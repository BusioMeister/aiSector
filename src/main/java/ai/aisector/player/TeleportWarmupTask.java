package ai.aisector.player; // Upewnij się, że pakiet jest poprawny

import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import com.google.gson.JsonObject;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class TeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final JsonObject targetLocation;
    private final String targetServerName;
    private final RedisManager redisManager;
    private int timeLeft = 5; // 5 sekund
    private final BossBar bossBar;

    public TeleportWarmupTask(Player player, JsonObject targetLocation, String targetServerName, RedisManager redisManager) {
        this.player = player;
        this.targetLocation = targetLocation;
        this.targetServerName = targetServerName;
        this.redisManager = redisManager;
        this.bossBar = org.bukkit.Bukkit.createBossBar("§aTeleportacja za: §e" + timeLeft, BarColor.GREEN, BarStyle.SOLID);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        if (timeLeft <= 0) {
            this.cancel();
            player.sendMessage("§aTeleportacja...");
            transferPlayer();
            return;
        }

        bossBar.setTitle("§aTeleportacja za: §e" + timeLeft);
        bossBar.setProgress((double) timeLeft / 5.0);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cNie ruszaj się! §7Teleportacja za §e" + timeLeft + "s"));
        timeLeft--;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        bossBar.removeAll();
    }

    private void transferPlayer() {
        try (Jedis jedis = redisManager.getJedis()) {
            // 1. Zapisz dane gracza (ekwipunek itp.) tuż przed transferem
            String playerData = PlayerDataSerializer.serialize(player, player.getLocation());
            jedis.setex("player:data:" + player.getUniqueId(), 60, playerData);

            // 2. Zapisz dokładną lokalizację docelową
            jedis.setex("player:teleport_location:" + player.getUniqueId(), 60, targetLocation.toString());

            // 3. Wyślij prośbę o transfer do Velocity
            jedis.publish("sector-transfer", player.getUniqueId() + ":" + targetServerName);
        }
    }
}