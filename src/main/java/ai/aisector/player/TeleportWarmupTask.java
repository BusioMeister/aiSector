package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.SectorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class TeleportWarmupTask extends BukkitRunnable {

    private final Player playerToTeleport;
    private final String targetPlayerName; // Zmieniamy na String, bo cel może być na innym serwerze
    private final Location startLocation;
    private final RedisManager redisManager;
    private final SectorManager sectorManager;
    private int countdown = 5;

    public TeleportWarmupTask(Player playerToTeleport, String targetPlayerName, RedisManager redisManager, SectorManager sectorManager) {
        this.playerToTeleport = playerToTeleport;
        this.targetPlayerName = targetPlayerName;
        this.redisManager = redisManager;
        this.sectorManager = sectorManager;
        this.startLocation = playerToTeleport.getLocation();

        playerToTeleport.sendMessage("§aTeleportacja do §e" + targetPlayerName + "§a rozpocznie się za 5 sekund. Nie ruszaj się!");
    }

    @Override
    public void run() {
        if (!playerToTeleport.isOnline()) {
            this.cancel();
            return;
        }

        if (startLocation.distance(playerToTeleport.getLocation()) > 0.5) {
            playerToTeleport.sendMessage("§cTeleportacja anulowana. Ruszyłeś się!");
            this.cancel();
            return;
        }

        if (countdown > 0) {
            playerToTeleport.sendActionBar(
                    Component.text("Teleportacja za: ", NamedTextColor.GRAY)
                            .append(Component.text(countdown, NamedTextColor.GREEN, TextDecoration.BOLD))
            );
            countdown--;
        } else {
            playerToTeleport.sendActionBar(Component.text("TELEPORTACJA...", NamedTextColor.GOLD, TextDecoration.BOLD));

            // 🔥 POPRAWIONA LOGIKA: Zleć transfer przez Velocity
            try (Jedis jedis = redisManager.getJedis()) {
                // To jest "prośba do admina /tp", ale wykonana automatycznie
                String requestJson = "{\"adminName\":\"" + playerToTeleport.getName() + "\",\"targetName\":\"" + targetPlayerName + "\"}";
                jedis.publish("aisector:tp_request", requestJson);
            }
            this.cancel();
        }
    }
}