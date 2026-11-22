package ai.aisector.listeners;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.shared.PacketEnvelope;
import ai.aisector.shared.Packet;
import ai.aisector.shared.TransferSaveRequest;
import ai.aisector.redis.packet.*;
import ai.aisector.task.LocalTeleportWarmupTask;
import ai.aisector.task.TeleportWarmupTask;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class CommandResponseListener extends JedisPubSub {
    private static final String SAVE_AND_ACK_SCRIPT = "if redis.call('get', KEYS[2]) ~= ARGV[5] then return -1; end; "
            + "local previous = redis.call('get', KEYS[1]); local previousTtl = redis.call('ttl', KEYS[1]); "
            + "redis.call('setex', KEYS[1], ARGV[1], ARGV[2]); "
            + "redis.call('setex', KEYS[3], ARGV[1], ARGV[5] .. ':' .. ARGV[2]); "
            + "local subscribers = redis.call('publish', ARGV[3], ARGV[4]); "
            + "if subscribers == 0 then "
            + "if previous then if previousTtl > 0 then redis.call('setex', KEYS[1], previousTtl, previous); "
            + "elseif previousTtl == -1 then redis.call('set', KEYS[1], previous); "
            + "else redis.call('del', KEYS[1]); end else redis.call('del', KEYS[1]); end; "
            + "redis.call('del', KEYS[3]); "
            + "if redis.call('get', KEYS[2]) == ARGV[5] then redis.call('del', KEYS[2]); end; "
            + "return 0; end; "
            + "if redis.call('get', KEYS[2]) == ARGV[5] then redis.call('del', KEYS[2]); end; "
            + "return subscribers";
    private static final String PUBLISH_SAVE_FAILURE_SCRIPT = "if redis.call('get', KEYS[1]) ~= ARGV[1] then return -1; end; "
            + "local subscribers = redis.call('publish', ARGV[2], ARGV[3]); "
            + "if subscribers > 0 and redis.call('get', KEYS[1]) == ARGV[1] then redis.call('del', KEYS[1]); end; "
            + "return subscribers";

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final Gson gson = new Gson();
    private final java.util.concurrent.ConcurrentMap<String, Player> sealedRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public CommandResponseListener(SectorPlugin plugin, SectorManager sectorManager, WorldBorderManager borderManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
        this.borderManager = borderManager;
    }
    private void handlePacketMessage(String message) {
        try {
            PacketEnvelope env = gson.fromJson(message, PacketEnvelope.class);
            if (env == null || !env.isValid()) {
                plugin.getLogger().warning("Odrzucono nieprawidłowy pakiet Redis.");
                return;
            }
            PacketCodec codec = PacketRegistry.get(env.id);
            if (codec == null) {
                plugin.getLogger().warning("Nieznany packetId=" + env.id);
                return;
            }
            Packet packet = (Packet) codec.decode(env.payload);
            PacketBus.dispatch(packet);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nie udało się obsłużyć pakietu Redis: " + exception.getMessage());
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (channel == null || message == null || message.length() > 1_048_576) {
            plugin.getLogger().warning("Ignoring empty or oversized Redis command message on " + channel);
            return;
        }
        BukkitTaskScheduler.execute(plugin, () -> {
              try {
            if (channel.equals(ai.aisector.ranking.DeathAnnouncements.CHANNEL)) {
                ai.aisector.ranking.DeathAnnouncements.broadcast(plugin, message);
                return;
            }
            if (channel.equals(RedisChannels.PACKET) || channel.startsWith(RedisChannels.PACKET + ":")) {
                plugin.getLogger().fine("Redis packet received on " + channel + " (" + message.length() + " chars)");

                handlePacketMessage(message);
                return;
            }

            JsonObject data = gson.fromJson(message, JsonObject.class);
            if (data == null) return;


            if (channel.equals(RedisChannels.SAVE_PLAYER_DATA)) {
                TransferSaveRequest request = TransferSaveRequest.parse(
                        stringField(data, "requestId"), stringField(data, "uuid")).orElse(null);
                if (request == null) {
                    plugin.getLogger().warning("Odrzucono żądanie zapisu gracza bez requestId/uuid.");
                    return;
                }
                String requestId = request.requestId();
                UUID playerUuid = request.playerUuid();
                String uuidString = playerUuid.toString();

                Player playerToSave = Bukkit.getPlayer(playerUuid);
                if (data.has("cancelled") && data.get("cancelled").isJsonPrimitive()
                        && data.getAsJsonPrimitive("cancelled").isBoolean() && data.get("cancelled").getAsBoolean()) {
                    if (playerToSave != null && sealedRequests.remove(requestId, playerToSave)) {
                        plugin.getUserManager().releaseUnpublishedTransfer(playerToSave);
                        plugin.getLogger().info("Released cancelled transfer " + requestId + " for " + playerToSave.getName());
                    } else if (playerToSave != null && plugin.getUserManager().abortSectorTransfer(playerToSave, UUID.fromString(requestId))) {
                        playerToSave.sendMessage(Messages.text("sector-manager.transfer-nie-powiodl-sie-pozostajesz"));
                        plugin.getLogger().info("Released cancelled sector transfer " + requestId + " for " + playerToSave.getName());
                    }
                    return;
                }
                // The request is broadcast to every backend; only the backend currently
                // hosting this player may acknowledge it.
                if (playerToSave == null || !playerToSave.isOnline()) {
                    return;
                }
                if (sectorManager.isLockedSpawnBackend() && !allowsLockedSpawnAdminTransfer(data, playerToSave.isOp())) {
                    publishSaveAcknowledgementAsync(requestId, uuidString, false);
                    return;
                }

                final String snapshot;
                String playerName = playerToSave.getName();
                try {
                    snapshot = plugin.getUserManager()
                            .serializePlayerDataForTransfer(playerToSave, playerToSave.getLocation());
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Nie udało się zapisać danych transferowych dla "
                            + playerName + ": " + exception.getMessage());
                    publishSaveAcknowledgementAsync(requestId, uuidString, false);
                    return;
                }

                JsonObject acknowledgement = new JsonObject();
                acknowledgement.addProperty("requestId", requestId);
                acknowledgement.addProperty("uuid", uuidString);
                acknowledgement.addProperty("success", true);
                plugin.getUserManager().prepareTransferProfileAsync(playerToSave)
                        .thenCompose(saved -> {
                    sealedRequests.put(requestId, playerToSave);
                    BukkitTaskScheduler.execute(plugin, () -> Bukkit.getScheduler().runTaskLater(plugin,
                            () -> sealedRequests.remove(requestId, playerToSave), 1200L));
                    return plugin.getRedisManager().executeAsync(jedis -> {
                    Object result = jedis.eval(SAVE_AND_ACK_SCRIPT,
                             java.util.List.of("player:data:" + uuidString,
                                     "player:transfer_save_request:" + uuidString, "player:data:" + uuidString + ":request"),
                            java.util.List.of("60", snapshot, RedisChannels.SAVE_PLAYER_DATA_ACK,
                                    acknowledgement.toString(), requestId));
                    if (!(result instanceof Number status)) {
                        throw new IllegalStateException("Unexpected transfer save result");
                    }
                    return status.longValue() > 0;
                });
                }).whenComplete((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().severe("Nie udało się zapisać danych transferowych dla "
                                + playerName + ": " + error.getMessage());
                        publishSaveAcknowledgementAsync(requestId, uuidString, false);
                    } else if (Boolean.TRUE.equals(ignored)) {
                        plugin.getLogger().info("Zapisano dane transferowe dla " + playerName + ".");
                    } else {
                        BukkitTaskScheduler.execute(plugin, () -> {
                            if (sealedRequests.remove(requestId, playerToSave))
                                plugin.getUserManager().releaseUnpublishedTransfer(playerToSave);
                        });
                        plugin.getLogger().fine("Ignoring superseded transfer save request " + requestId
                                + " for " + playerName);
                    }
                });
                return;
            }
//            if (channel.equals("aisector:send_message")) {
//                Player player = Bukkit.getPlayer(data.get("playerName").getAsString());
//                if (player != null) player.sendMessage(data.get("message").getAsString());
//            }
            if (channel.equals(RedisChannels.TP_EXECUTE_LOCAL)) {
                CommandRedisPayloads.LocalTeleport request = CommandRedisPayloads.localTeleport(data).orElse(null);
                if (request == null) return;
                Player admin = Bukkit.getPlayer(request.playerName());
                Player target = Bukkit.getPlayer(request.targetName());
                if (admin != null && target != null) {
                    admin.teleport(target.getLocation());
                }

                // --- POCZĄTEK NOWEGO KODU DLA /TP ---
            } else if (channel.equals(RedisChannels.GET_LOCATION_FOR_ADMIN_TP)) {
                CommandRedisPayloads.AdminLocationRequest request =
                        CommandRedisPayloads.adminLocationRequest(data).orElse(null);
                if (request == null) return;
                String targetName = request.targetName();
                Player targetPlayer = Bukkit.getPlayerExact(targetName);

                // Sprawdzamy, czy gracz, którego szuka Velocity, jest na TYM serwerze
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    Location loc = targetPlayer.getLocation();
                    if (loc.getWorld() == null) return;

                    // Tak, gracz jest u nas! Odpowiadamy do Velocity z jego pełną lokalizacją.
                    JsonObject response = new JsonObject();
                    response.addProperty("adminUUID", request.adminUuid().toString());
                    response.addProperty("targetUUID", targetPlayer.getUniqueId().toString());
                    String targetServerName = plugin.getConfig().getString("this-sector-name", "");
                    if (targetServerName.isBlank() || !sectorManager.owns(loc)) {
                        plugin.getLogger().warning("Could not resolve sector for admin teleport target " + targetName);
                        return;
                    }
                    response.addProperty("targetServerName", targetServerName);

                    JsonObject locationJson = new JsonObject();
                    locationJson.addProperty("world", loc.getWorld().getName());
                    locationJson.addProperty("x", loc.getX());
                    locationJson.addProperty("y", loc.getY());
                    locationJson.addProperty("z", loc.getZ());
                    locationJson.addProperty("yaw", loc.getYaw());
                    locationJson.addProperty("pitch", loc.getPitch());
                    response.add("location", locationJson);

                    String responsePayload = response.toString();
                    plugin.getRedisManager().publishAsync(RedisChannels.ADMIN_LOCATION_RESPONSE, responsePayload)
                            .exceptionally(error -> {
                                plugin.getLogger().warning("Could not publish admin teleport location: "
                                        + error.getMessage());
                                return null;
                            });
                }
                // --- KONIEC NOWEGO KODU DLA /TP ---



            } else if (channel.equals(RedisChannels.GLOBAL_WEATHER_CHANGE)) {
                String weatherType = CommandRedisPayloads.weatherType(data).orElse(null);
                if (weatherType == null) return;
                for (World world : Bukkit.getWorlds()) {
                    switch (weatherType) {
                        case "clear": world.setStorm(false); world.setThundering(false); break;
                        case "rain": world.setStorm(true); world.setThundering(false); break;
                        case "thunder": world.setStorm(true); world.setThundering(true); break;
                    }
                }
            } else if (channel.equals(RedisChannels.GLOBAL_TIME_CHANGE)) {
                String timeType = CommandRedisPayloads.timeType(data).orElse(null);
                if (timeType == null) return;
                long timeToSet = timeType.equals("night") ? 13000L : 1000L;
                for (World world : Bukkit.getWorlds()) {
                    world.setTime(timeToSet);
                }
            }

              } catch (RuntimeException exception) {
                  plugin.getLogger().warning("Ignoring malformed Redis command message on " + channel + ": "
                          + exception.getMessage());
              }
            });
    }

    private void publishSaveAcknowledgementAsync(String requestId, String uuid, boolean saved) {
        JsonObject acknowledgement = new JsonObject();
        acknowledgement.addProperty("requestId", requestId);
        acknowledgement.addProperty("uuid", uuid);
        acknowledgement.addProperty("success", saved);
        plugin.getRedisManager().executeAsync(jedis -> jedis.eval(PUBLISH_SAVE_FAILURE_SCRIPT,
                        java.util.Collections.singletonList("player:transfer_save_request:" + uuid),
                        java.util.List.of(requestId, RedisChannels.SAVE_PLAYER_DATA_ACK,
                                acknowledgement.toString())))
                .exceptionally(error -> {
                    plugin.getLogger().severe("Nie udało się potwierdzić zapisu gracza " + uuid + ": "
                            + error.getMessage());
                    return null;
                });
    }

    private static String stringField(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()
                || !data.getAsJsonPrimitive(key).isString()) return null;
        return data.get(key).getAsString();
    }

    static boolean allowsLockedSpawnAdminTransfer(JsonObject data, boolean operator) {
        String purpose = stringField(data, "purpose");
        // admin-summon is set by Velocity only for a request issued by a Paper operator.
        // Here the player whose snapshot is saved is the summon target, not the operator.
        return "admin-summon".equals(purpose) || (operator && "admin-tp".equals(purpose));
    }

}
