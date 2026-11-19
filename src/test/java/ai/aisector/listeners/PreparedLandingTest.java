package ai.aisector.listeners;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedLandingTest {
    @Test
    void recognizesTheExactTransferEvenWhenThePlayerHasMovedSinceTheirInitialSpawn() {
        Location initial = new Location(null, -0.35, 100, 25);
        var landing = new PlayerDataListener.PreparedLanding("{\"x\":-0.35}", initial);
        initial.add(-12, 0, 3);
        assertTrue(landing.matches("{\"x\":-0.35}"));
        assertFalse(landing.matches("{\"x\":-12.35}"));
        assertFalse(landing.matches(null));
    }
}
