package ai.aisector.task;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.UserManager;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;

public class BaseTeleportWarmupTask extends BukkitRunnable {

    private static final Gson GSON = new Gson();

    private final Player player;
    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private final UserManager userManager;

    private final Location startLocation;

    private final String targetSector;
    private final Location targetLocation;

    private final int warmupSeconds;
    private final String bossBarTitlePrefix;
    private final String actionBarTextPrefix;
    private final String successMessage;

    private final String redisKey;
    private final int redisTtlSeconds;

    private int timeLeft;
    private final BossBar bossBar;

    public BaseTeleportWarmupTask(Player player,
                                  Location targetLocation,
                                  String targetSector,
                                  int warmupSeconds,
                                  String bossBarTitlePrefix,
                                  String actionBarTextPrefix,
                                  String successMessage,
                                  String redisKey,
                                  int redisTtlSeconds,
                                  SectorPlugin plugin) {

        this.player = player;
        this.startLocation = player.getLocation();

        this.targetSector = targetSector;
        this.targetLocation = targetLocation;

        this.warmupSeconds = warmupSeconds;
        this.timeLeft = warmupSeconds;

        this.bossBarTitlePrefix = bossBarTitlePrefix;
        this.actionBarTextPrefix = actionBarTextPrefix;
        this.successMessage = successMessage;

        this.redisKey = redisKey;
        this.redisTtlSeconds = redisTtlSeconds;

        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
        this.sectorManager = plugin.getSectorManager();
        this.userManager = plugin.getUserManager();

        this.bossBar = Bukkit.createBossBar(
                bossBarTitlePrefix + timeLeft,
                BarColor.GREEN,
                BarStyle.SOLID
        );
    }

    public void start() {
        bossBar.addPlayer(player);
        runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        if (player.getLocation().distanceSquared(startLocation) > 1.0) {
            player.sendMessage("§cTeleportacja anulowana! Ruszyłeś się.");
            cancel();
            return;
        }

        if (timeLeft <= 0) {
            cancel();
            teleportNow();
            return;
        }

        bossBar.setTitle(bossBarTitlePrefix + timeLeft);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) timeLeft / (double) warmupSeconds)));

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                actionBarTextPrefix + timeLeft + "s"
        ));

        timeLeft--;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        bossBar.removeAll();
    }

    private void teleportNow() {
        World world = targetLocation.getWorld();
        if (world == null) {
            player.sendMessage("§cWystąpił błąd: świat docelowy nie istnieje.");
            return;
        }

        String currentSector = sectorManager.getSectorForLocation(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ()
        );

        if (targetSector.equals(currentSector)) {
            player.teleport(targetLocation);
            player.sendMessage(successMessage);
            return;
        }

        player.sendMessage("§7Ukończono odliczanie, rozpoczynam transfer...");

        // zapis danych gracza do transferu
        userManager.savePlayerDataForTransfer(player, player.getLocation());

        // zapis finalnego celu teleportu (na serwer docelowy)
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.setex(redisKey, redisTtlSeconds, locationToJson(targetLocation));
        }

        // transfer
        sectorManager.transferPlayer(player.getUniqueId(), targetSector);
    }

    public static String locationToJson(Location loc) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        return GSON.toJson(data);
    }
}
