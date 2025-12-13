package ai.aisector.redis.packet;

import com.google.gson.Gson;

public class JsonPacketCodec<T extends Packet> implements PacketCodec<T> {
    private static final Gson GSON = new Gson();
    private final Class<T> type;

    public JsonPacketCodec(Class<T> type) {
        this.type = type;
    }

    @Override
    public String encode(T packet) {
        return GSON.toJson(packet);
    }

    @Override
    public T decode(String json) {
        return GSON.fromJson(json, type);
    }
}
