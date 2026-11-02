package ai.aisector.guilds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRegionBoundsTest {
    @Test
    void boundsAreInclusiveSquareAndRejectNonFiniteInputs() {
        assertTrue(GuildRegionBounds.contains(11, -11, 0, 0, 11));
        assertFalse(GuildRegionBounds.contains(11.001, 0, 0, 0, 11));
        assertFalse(GuildRegionBounds.contains(0, Double.NaN, 0, 0, 11));
        assertFalse(GuildRegionBounds.contains(0, 0, 0, 0, Double.POSITIVE_INFINITY));
    }
}
