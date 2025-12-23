package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.redis.packet.*;
import ai.aisector.task.LocalTeleportWarmupTask;
import ai.aisector.task.TeleportWarmupTask;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis; // Upewnij się, że masz ten import
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

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
    private void handlePacketMessage(String message) {
        PacketEnvelope env = gson.fromJson(message, PacketEnvelope.class);
        PacketCodec codec = PacketRegistry.get(env.id);
        if (codec == null) {
            plugin.getLogger().warning("Nieznany packetId=" + env.id);
            return;
        }
        Packet packet = (Packet) codec.decode(env.payload);
        PacketBus.dispatch(packet);
    }

    @Override
    public void onMessage(String channel, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (channel.equals("aisector:packet")) {
                handlePacketMessage(message);
                return;
            }

            JsonObject data = gson.fromJson(message, JsonObject.class);


            if (channel.equals("aisector:save_player_data")) {
                String uuidString = data.get("uuid").getAsString();
                Player playerToSave = Bukkit.getPlayer(UUID.fromString(uuidString));
                if (playerToSave != null && playerToSave.isOnline()) {
                    // Wykonujemy zapis danych na prośbę od Velocity
                    plugin.getUserManager().savePlayerDataForTransfer(playerToSave, playerToSave.getLocation());
                    // Informujemy w konsoli, że zapis się odbył
                    plugin.getLogger().info("Zapisano dane transferowe dla " + playerToSave.getName() + " na żądanie z proxy.");
                }
                return; // Ważne, aby zakończyć działanie po obsłużeniu tego kanału
            }
//            if (channel.equals("aisector:send_message")) {
//                Player player = Bukkit.getPlayer(data.get("playerName").getAsString());
//                if (player != null) player.sendMessage(data.get("message").getAsString());
//            }
            if (channel.equals("aisector:tp_execute_local")) {
                Player admin = Bukkit.getPlayer(data.get("playerName").getAsString());
                Player target = Bukkit.getPlayer(data.get("targetName").getAsString());
                if (admin != null && target != null) {
                    admin.teleport(target.getLocation());
                }

                // --- POCZĄTEK NOWEGO KODU DLA /TP ---
            } else if (channel.equals("aisector:get_location_for_admin_tp")) {
                String targetName = data.get("targetName").getAsString();
                Player targetPlayer = Bukkit.getPlayer(targetName);

                // Sprawdzamy, czy gracz, którego szuka Velocity, jest na TYM serwerze
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    String adminUUID = data.get("adminUUID").getAsString();
                    Location loc = targetPlayer.getLocation();

                    // Tak, gracz jest u nas! Odpowiadamy do Velocity z jego pełną lokalizacją.
                    JsonObject response = new JsonObject();
                    response.addProperty("adminUUID", adminUUID);
                    response.addProperty("targetServerName", sectorManager.getSectorForLocation(loc.getBlockX(), loc.getBlockZ()));

                    JsonObject locationJson = new JsonObject();
                    locationJson.addProperty("world", loc.getWorld().getName());
                    locationJson.addProperty("x", loc.getX());
                    locationJson.addProperty("y", loc.getY());
                    locationJson.addProperty("z", loc.getZ());
                    locationJson.addProperty("yaw", loc.getYaw());
                    locationJson.addProperty("pitch", loc.getPitch());
                    response.add("location", locationJson);

                    try (Jedis jedis = plugin.getRedisManager().getJedis()) {
                        // Wysyłamy odpowiedź, której nasłuchuje Velocity
                        jedis.publish("aisector:admin_location_response", response.toString());
                    }
                }
                // --- KONIEC NOWEGO KODU DLA /TP ---



            } else if (channel.equals("aisector:global_weather_change")) {
                String weatherType = data.get("weatherType").getAsString();
                for (World world : Bukkit.getWorlds()) {
                    switch (weatherType) {
                        case "clear": world.setStorm(false); world.setThundering(false); break;
                        case "rain": world.setStorm(true); world.setThundering(false); break;
                        case "thunder": world.setStorm(true); world.setThundering(true); break;
                    }
                }
            } else if (channel.equals("aisector:global_time_change")) {
                String timeType = data.get("timeType").getAsString();
                long timeToSet = timeType.equals("night") ? 13000L : 1000L;
                for (World world : Bukkit.getWorlds()) {
                    world.setTime(timeToSet);
                }
            }

        });
    }
}