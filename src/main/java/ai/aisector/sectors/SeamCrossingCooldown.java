package ai.aisector.sectors;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Local to the receiving backend; used only to gate movement across a shared seam. */
public final class SeamCrossingCooldown {
    private static final long COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(2);
    public enum Crossing { ALLOWED, BLOCKED_NOTIFY, BLOCKED }
    private record Arrival(long at, boolean notified) {}
    private final Map<UUID, Arrival> arrivals = new HashMap<>();
    private final LongSupplier now;

    public SeamCrossingCooldown() { this(System::nanoTime); }

    SeamCrossingCooldown(LongSupplier now) { this.now = now; }

    public void arrived(UUID player) { arrivals.put(player, new Arrival(now.getAsLong(), false)); }

    public Crossing crossingState(UUID player) {
        Arrival arrived = arrivals.get(player);
        if (arrived == null) return Crossing.ALLOWED;
        if (now.getAsLong() - arrived.at() >= COOLDOWN_NANOS) {
            arrivals.remove(player);
            return Crossing.ALLOWED;
        }
        if (arrived.notified()) return Crossing.BLOCKED;
        arrivals.put(player, new Arrival(arrived.at(), true));
        return Crossing.BLOCKED_NOTIFY;
    }

    public void clear(UUID player) { arrivals.remove(player); }
}
