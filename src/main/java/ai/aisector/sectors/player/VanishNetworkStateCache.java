package ai.aisector.sectors.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-process view of vanish events received from other Paper backends. */
final class VanishNetworkStateCache {
    private final Map<UUID, String> namesByUuid = new ConcurrentHashMap<>();
    private long revision;

    synchronized void setVanished(UUID uuid, String name) {
        if (uuid == null || name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        namesByUuid.put(uuid, name);
        revision++;
    }

    synchronized void clear(UUID uuid) {
        if (uuid != null) { namesByUuid.remove(uuid); revision++; }
    }

    Map<UUID, String> snapshot() {
        return Map.copyOf(namesByUuid);
    }

    synchronized long revision() { return revision; }

    synchronized boolean replaceSnapshot(long expected, Map<UUID, String> snapshot, java.util.Set<UUID> local) {
        if (revision != expected) return false;
        if (snapshot.size() > 4096 || snapshot.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || !entry.getValue().matches("[A-Za-z0-9_]{1,16}")))
            throw new IllegalArgumentException("Invalid vanish snapshot");
        namesByUuid.clear();
        snapshot.forEach((uuid, name) -> { if (!local.contains(uuid)) namesByUuid.put(uuid, name); });
        revision++;
        return true;
    }
}
