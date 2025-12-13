package ai.aisector.redis.packet;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PacketBus {
    private static final Map<Integer, List<PacketListener<?>>> LISTENERS = new ConcurrentHashMap<>();

    private PacketBus() {}

    public static <T extends Packet> void register(int packetId, PacketListener<T> listener) {
        LISTENERS.computeIfAbsent(packetId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public static void dispatch(Packet packet) {
        List<PacketListener<?>> ls = LISTENERS.get(packet.id());
        if (ls == null) return;
        for (PacketListener l : ls) l.handle(packet);
    }
}
