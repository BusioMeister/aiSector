package ai.aisector.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingTest {
    @Test void gainAndLossUseSeparateCurves() {
        assertEquals(30, Rating.award(1000, 1000));
        assertEquals(32, Rating.loss(1000, 1000));
        assertEquals(35, Rating.award(1000, 1250));
        assertEquals(37, Rating.loss(1000, 1250));
        assertEquals(49, Rating.award(1000, 2000));
        assertEquals(50, Rating.loss(1000, 2000));
        assertEquals(0, Rating.loss(1300, 1000));
        assertEquals(0, Rating.award(1300, 1000));
        assertEquals(0, Rating.loss(1000, 2));
        assertEquals(0, Rating.award(Rating.MAX, 1250));
    }

    @Test void adminAdjustmentsClampWithoutIntegerOverflow() {
        assertEquals(Rating.MAX, AdminStatsCommand.Stat.RANKING.updated("add", 1000, Integer.MAX_VALUE));
        assertEquals(0, AdminStatsCommand.Stat.DEATHS.updated("add", 2, -3));
        assertEquals(13, AdminStatsCommand.Stat.KILLSTREAK.updated("set", 5, 13));
    }
}
