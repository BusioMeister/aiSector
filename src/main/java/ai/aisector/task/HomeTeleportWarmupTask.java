package ai.aisector.task;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import com.google.gson.JsonObject;
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

public class HomeTeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final RedisManager redisManager;

    private final Location startLocation;
    private final String targetSector;
    private final JsonObject targetLocationJson;
    private final int homeSlot;
    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final UserManager userManager;
    private int timeLeft = 5;
    private final BossBar bossBar;

    public HomeTeleportWarmupTask(Player player, String targetSector, JsonObject targetLocationJson, int homeSlot, SectorPlugin plugin) {
        this.player = player;
        this.startLocation = player.getLocation();
        this.targetSector = targetSector;
        this.targetLocationJson = targetLocationJson;
        this.homeSlot = homeSlot;
        this.redisManager = plugin.getRedisManager();

        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.userManager = plugin.getUserManager();
        this.bossBar = Bukkit.createBossBar("§aTeleportacja do domu za: §e" + timeLeft, BarColor.GREEN, BarStyle.SOLID);
    }

    public void start() {
        this.bossBar.addPlayer(player);
        this.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

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

        World world = Bukkit.getWorld(targetLocationJson.get("world").getAsString());
        if (world == null) {
            player.sendMessage("§cWystąpił błąd: świat docelowy nie istnieje.");
            return;
        }

        Location targetLocation = new Location(
                world,
                targetLocationJson.get("x").getAsDouble(),
                targetLocationJson.get("y").getAsDouble(),
                targetLocationJson.get("z").getAsDouble(),
                targetLocationJson.get("yaw").getAsFloat(),
                targetLocationJson.get("pitch").getAsFloat());

        if (targetSector.equals(currentSector)) {
            // Teleport lokalny
            player.teleport(targetLocation);
            player.sendMessage("§aPrzeteleportowano do domu #" + homeSlot + "!");
        } else {
            player.sendMessage("§7Ukończono odliczanie, rozpoczynam transfer...");

            // 🔥 OSTATECZNA POPRAWKA: Używamy Redis do przekazania celu 🔥
            userManager.savePlayerDataForTransfer(player, player.getLocation());
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.setex("player:final_teleport_target:" + player.getUniqueId(), 60, locationToJson(targetLocation));
            }
            sectorManager.transferPlayer(player.getUniqueId(), targetSector);
        }
    }

    private String locationToJson(Location loc) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX()); data.put("y", loc.getY()); data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw()); data.put("pitch", loc.getPitch());
        return new com.google.gson.Gson().toJson(data);
    }
}