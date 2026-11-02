package ai.aisector.redis.packet;

import ai.aisector.shared.Packet;
public interface PacketListener<T extends Packet> {
    void handle(T packet);
}
