package ai.aisector.redis.packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketRegistry {
    private static final Map<Integer, PacketCodec<?>> CODECS = new ConcurrentHashMap<>();

    private PacketRegistry() {}

    public static <T extends Packet> void register(int id, PacketCodec<T> codec) {
        CODECS.put(id, codec);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Packet> PacketCodec<T> get(int id) {

        return (PacketCodec<T>) CODECS.get(id);
    }

}
