package ai.aisector.redis.packet.impl;

import ai.aisector.redis.packet.Packet;

public class SendMessagePacket implements Packet {
    public String playerName;
    public String message;

    public SendMessagePacket() {}

    @Override
    public int id() {
        return 2;
    }
}
