package ai.aisector.redis.packet;

import ai.aisector.shared.Packet;
public interface PacketCodec<T extends Packet> {
    String encode(T packet);
    T decode(String json);
}
