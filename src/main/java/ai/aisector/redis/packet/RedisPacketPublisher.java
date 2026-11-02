package ai.aisector.redis.packet;

import ai.aisector.database.RedisManager;
import ai.aisector.shared.PacketEnvelope;
import ai.aisector.shared.Packet;
import com.google.gson.Gson;

import java.util.concurrent.CompletableFuture;

public class RedisPacketPublisher {
    private static final Gson GSON = new Gson();
    private final RedisManager redis;

    public RedisPacketPublisher(RedisManager redis) {
        this.redis = redis;
    }

    public CompletableFuture<Long> publishAsync(String channel, Packet packet) {
        PacketCodec codec = PacketRegistry.get(packet.id());
        if (codec == null) throw new IllegalStateException("Brak codec dla packetId=" + packet.id());

        PacketEnvelope env = new PacketEnvelope(packet.id(), codec.encode(packet));
        return redis.publishAsync(channel, GSON.toJson(env));
    }
}
