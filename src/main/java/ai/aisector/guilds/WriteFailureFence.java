package ai.aisector.guilds;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Rejects already-enqueued snapshots after their common cache base was invalidated. */
final class WriteFailureFence {
    private final Map<String, Long> invalidThrough = new ConcurrentHashMap<>();

    void invalidateThrough(String key, long version) { invalidThrough.merge(key, version, Math::max); }
    boolean rejects(String key, long version) { return version <= invalidThrough.getOrDefault(key, -1L); }
}
