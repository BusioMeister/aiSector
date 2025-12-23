package ai.aisector.redis.packet.impl;

import ai.aisector.redis.packet.Packet;

public class AlertPacket implements Packet {
    public String message;

    public AlertPacket() {}

    @Override
    public int id() {
        return 3;
    }
}
