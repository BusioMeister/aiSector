package ai.aisector.redis.packet;

public interface PacketListener<T extends Packet> {
    void handle(T packet);
}
