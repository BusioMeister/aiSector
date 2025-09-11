package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
                return;
            }

            JsonObject data = gson.fromJson(message, JsonObject.class);

            if (channel.equals("aisector:send_message")) {
                Player player = Bukkit.getPlayer(data.get("playerName").getAsString());
                if (player != null) player.sendMessage(data.get("message").getAsString());
            } else if (channel.equals("aisector:tp_execute_local")) {
                Player admin = Bukkit.getPlayer(data.get("playerName").getAsString());
                Player target = Bukkit.getPlayer(data.get("targetName").getAsString());
                if (admin != null && target != null) {
                    teleportPlayerLocally(admin, target.getLocation(), "§aPomyślnie przeteleportowano do §e" + target.getName());
                }
            } else if (channel.equals("aisector:tpa_initiate_warmup")) {
                Player requester = Bukkit.getPlayer(data.get("requesterName").getAsString());
                if (requester != null) {
                    JsonObject targetLocation = data.getAsJsonObject("targetLocation");
                    String targetServerName = data.get("targetServerName").getAsString();
                    new TeleportWarmupTask(requester, targetLocation, targetServerName, plugin.getRedisManager()).runTaskTimer(plugin, 0L, 20L);
                }
            } else if (channel.equals("aisector:tp_execute_local_tpa")) {
                // 🔥 TUTAJ JEST JEDYNA ZMIANA 🔥
                // Zamiast teleportować od razu, uruchamiamy nowe zadanie z odliczaniem.
                Player playerToTeleport = Bukkit.getPlayer(data.get("playerToTeleportName").getAsString());
                if (playerToTeleport != null) {
                    JsonObject locData = data.getAsJsonObject("targetLocation");
                    World world = Bukkit.getWorld(locData.get("world").getAsString());
                    if (world != null) {
                        Location targetLocation = new Location(world, locData.get("x").getAsDouble(), locData.get("y").getAsDouble(), locData.get("z").getAsDouble(), locData.get("yaw").getAsFloat(), locData.get("pitch").getAsFloat());

                        // Uruchomienie nowego zadania z odliczaniem
                        new LocalTeleportWarmupTask(playerToTeleport, targetLocation, "§aZostałeś przeteleportowany.", sectorManager, borderManager).runTaskTimer(plugin, 0L, 20L);
                    }
                }
            }else if (channel.equals("aisector:global_weather_change")) {
                String weatherType = data.get("weatherType").getAsString();
                String adminName = data.get("admin").getAsString();

                for (World world : Bukkit.getWorlds()) {
                    switch (weatherType) {
                        case "clear":
                            world.setStorm(false);
                            world.setThundering(false);
                            break;
                        case "rain":
                            world.setStorm(true);
                            world.setThundering(false);
                            break;
                        case "thunder":
                            world.setStorm(true);
                            world.setThundering(true);
                            break;
                    }
                }
            }
            else if (channel.equals("aisector:global_time_change")) {
                String timeType = data.get("timeType").getAsString();
                String adminName = data.get("admin").getAsString();
                long timeToSet = 1000L; // Domyślnie dzień (poranek)

                if (timeType.equals("night")) {
                    timeToSet = 13000L; // Noc
                }

                for (World world : Bukkit.getWorlds()) {
                    world.setTime(timeToSet);
                }
            }
        });
    }

    // Ta metoda nie jest już potrzebna, bo jej logikę przenieśliśmy do LocalTeleportWarmupTask
    // Możesz ją usunąć, aby kod był czystszy.
    private void teleportPlayerLocally(Player player, Location location, String successMessage) {
        CompletableFuture<Boolean> teleportFuture = player.teleportAsync(location);
        teleportFuture.thenAccept(success -> {
            if (success) {
                player.sendMessage(successMessage);
                borderManager.sendWorldBorder(player, sectorManager.getSector(location.getBlockX(), location.getBlockZ()));
            } else {
                player.sendMessage("§cLokalna teleportacja nie powiodła się.");
            }
        });
    }
}