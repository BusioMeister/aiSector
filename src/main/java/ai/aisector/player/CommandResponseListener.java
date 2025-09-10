package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class CommandResponseListener extends JedisPubSub {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final Gson gson = new Gson();

    public CommandResponseListener(SectorPlugin plugin, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
    }

    @Override
    public void onMessage(String channel, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {

            if (channel.equals("aisector:alert")) {
                Bukkit.broadcast(Component.text(message));
                return; // Zakończ, bo alert to tylko tekst
            }

            // Wszystkie inne kanały przesyłają dane w formacie JSON
            JsonObject data = gson.fromJson(message, JsonObject.class);

            if (channel.equals("aisector:send_message")) {
                Player player = Bukkit.getPlayer(data.get("playerName").getAsString());
                if (player != null) {
                    player.sendMessage(data.get("message").getAsString());
                }
            }
            else if (channel.equals("aisector:tp_execute_local")) {
                // 🔥 NOWA, POPRAWIONA LOGIKA DLA LOKALNEGO TELEPORTU
                String adminName = data.get("playerName").getAsString(); // W /tp wysyłamy jako 'playerName'
                String targetName = data.get("targetName").getAsString();
                Player admin = Bukkit.getPlayer(adminName);
                Player target = Bukkit.getPlayer(targetName);

                if (admin != null && target != null) {
                    CompletableFuture<Boolean> teleportFuture = admin.teleportAsync(target.getLocation());
                    teleportFuture.thenAccept(success -> {
                        if (success) {
                            admin.sendMessage("§aPomyślnie przeteleportowano do §e" + target.getName());
                            Sector currentSector = sectorManager.getSector(target.getLocation().getBlockX(), target.getLocation().getBlockZ());
                            if (currentSector != null) {
                                borderManager.sendWorldBorder(admin, currentSector);
                            }
                        } else {
                            admin.sendMessage("§cLokalna teleportacja nie powiodła się.");
                        }
                    });
                }
            }
            else if (channel.equals("aisector:tpa_initiate_warmup")) {
                String requesterName = data.get("requesterName").getAsString();
                String targetName = data.get("targetName").getAsString();
                Player requester = Bukkit.getPlayer(requesterName);

                if (requester != null) {
                    // Rozpocznij odliczanie dla gracza
                    new TeleportWarmupTask(requester, targetName, plugin.getRedisManager(), plugin.getSectorManager()).runTaskTimer(plugin, 0L, 20L);
                }
            }
        });
    }
}