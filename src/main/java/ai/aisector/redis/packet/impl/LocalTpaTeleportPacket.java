package ai.aisector.redis.packet.impl;

import ai.aisector.redis.packet.Packet;

public class LocalTpaTeleportPacket implements Packet {
    public String playerToTeleportName;

    public String world;
    public double x, y, z;
    public float yaw, pitch;

    public String message; // np. "§aZostałeś przeteleportowany."

    public LocalTpaTeleportPacket() {}

    @Override
    public int id() {
        return 4;
    }
}
