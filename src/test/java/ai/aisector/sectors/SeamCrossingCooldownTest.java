package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static ai.aisector.sectors.SeamCrossingCooldown.Crossing.*;

class SeamCrossingCooldownTest {
    @Test
    void blocksOnlyTheRecentlyArrivedPlayerAndReleasesAtOneSecond() {
        var now = new AtomicLong(10);
        var cooldown = new SeamCrossingCooldown(now::get);
        var player = UUID.randomUUID();
        assertEquals(ALLOWED, cooldown.crossingState(player));
        cooldown.arrived(player);
        now.addAndGet(500_000_000);
        assertEquals(BLOCKED_NOTIFY, cooldown.crossingState(player));
        assertEquals(ALLOWED, cooldown.crossingState(UUID.randomUUID()));
        now.addAndGet(499_999_999);
        assertEquals(BLOCKED, cooldown.crossingState(player));
        now.incrementAndGet();
        assertEquals(ALLOWED, cooldown.crossingState(player));
    }

    @Test
    void newArrivalResetsTheWindowAndQuitClearsIt() {
        var now = new AtomicLong();
        var cooldown = new SeamCrossingCooldown(now::get);
        var player = UUID.randomUUID();
        cooldown.arrived(player);
        now.addAndGet(900_000_000);
        cooldown.arrived(player);
        now.addAndGet(150_000_000);
        assertEquals(BLOCKED_NOTIFY, cooldown.crossingState(player));
        cooldown.clear(player);
        assertEquals(ALLOWED, cooldown.crossingState(player));
    }
}
