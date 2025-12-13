package ai.aisector.redis.packet;

public class PacketEnvelope {
    public int id;
    public String payload;   // JSON konkretnego pakietu
    public int version = 1;  // na przyszłość
}
