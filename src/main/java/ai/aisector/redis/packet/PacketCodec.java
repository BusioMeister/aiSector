package ai.aisector.redis.packet;

public interface PacketCodec<T extends Packet> {
    String encode(T packet);
    T decode(String json);
}
