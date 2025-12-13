package ai.aisector.redis.packet.impl;

import ai.aisector.redis.packet.Packet;

public class TpaInitiateWarmupPacket implements Packet {
    public String requesterName;
    public String targetServerName;

    // targetLocation
    public String world;
    public double x, y, z;
    public float yaw, pitch;

    public TpaInitiateWarmupPacket() {}

    public TpaInitiateWarmupPacket(String requesterName, String targetServerName,
                                   String world, double x, double y, double z, float yaw, float pitch) {
        this.requesterName = requesterName;
        this.targetServerName = targetServerName;
        this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
    }

    @Override
    public int id() {
        return 1;
    }
}
