package ai.aisector.redis.packet;

import ai.aisector.database.RedisManager;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

public class RedisPacketPublisher {
    private static final Gson GSON = new Gson();
    private final RedisManager redis;

    public RedisPacketPublisher(RedisManager redis) {
        this.redis = redis;
    }

    public void publish(String channel, Packet packet) {
        PacketCodec codec = PacketRegistry.get(packet.id());
        if (codec == null) throw new IllegalStateException("Brak codec dla packetId=" + packet.id());

        PacketEnvelope env = new PacketEnvelope();
        env.id = packet.id();
        env.payload = codec.encode(packet);

        try (Jedis jedis = redis.getJedis()) {
            jedis.publish(channel, GSON.toJson(env));
        }
    }
}
