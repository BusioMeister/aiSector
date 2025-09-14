package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager; // <-- ZMIANA
import ai.aisector.sectors.SectorManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis; // <-- ZMIANA

import java.util.HashMap; // <-- ZMIANA
import java.util.Map;   // <-- ZMIANA

public class TeleportWarmupTask extends BukkitRunnable {

    private final Player player;
    private final JsonObject targetLocationJson;
    private final String targetServerName;
    private final SectorPlugin plugin;
    private final UserManager userManager;
    private final SectorManager sectorManager;
    private final RedisManager redisManager; // <-- ZMIANA
    private int timeLeft = 5;
    private final BossBar bossBar;

    public TeleportWarmupTask(Player player, JsonObject targetLocation, String targetServerName, SectorPlugin plugin) {
        this.player = player;
        this.targetLocationJson = targetLocation;
        this.targetServerName = targetServerName;
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.sectorManager = plugin.getSectorManager();
        this.redisManager = plugin.getRedisManager(); // <-- ZMIANA
        this.bossBar = Bukkit.createBossBar("§aTeleportacja za: §e" + timeLeft, BarColor.GREEN, BarStyle.SOLID);
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
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    // <-- CAŁA TA METODA ZOSTAŁA PODMIENIONA -->
    private void transferPlayer() {
        User user = userManager.getUser(player);
        if (user == null) {
            player.sendMessage("§cWystąpił krytyczny błąd, nie można wczytać Twojego profilu.");
            return;
        }

        // 1. Tworzymy obiekt lokalizacji z danych JSON
        Location targetLocation = new Location(
                Bukkit.getWorld(targetLocationJson.get("world").getAsString()),
                targetLocationJson.get("x").getAsDouble(),
                targetLocationJson.get("y").getAsDouble(),
                targetLocationJson.get("z").getAsDouble(),
                targetLocationJson.get("yaw").getAsFloat(),
                targetLocationJson.get("pitch").getAsFloat()
        );

        // 2. Zapisujemy dane (ekwipunek, HP) do Redis za pomocą UserManager
        userManager.savePlayerDataForTransfer(player, player.getLocation());

        // 3. Zapisujemy OSTATECZNY cel teleportacji w Redis
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.setex("player:final_teleport_target:" + player.getUniqueId(), 60, locationToJson(targetLocation));
        }

        // 4. Zlecamy transfer gracza
        sectorManager.transferPlayer(player.getUniqueId(), targetServerName);
    }

    // <-- NOWA METODA POMOCNICZA -->
    private String locationToJson(Location loc) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        return new com.google.gson.Gson().toJson(data);
    }
}