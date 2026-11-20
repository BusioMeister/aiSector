package ai.aisector.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingTest {
    @Test void gainAndLossUseSeparateCurves() {
        assertEquals(30, Rating.award(1000, 1000));
        assertEquals(20, Rating.loss(1000, 1000));
        assertEquals(55, Rating.award(1000, 1250));
        assertEquals(28, Rating.loss(1000, 1250));
        assertEquals(70, Rating.award(1000, 1900));
        assertEquals(49, Rating.loss(1000, 1899));
        assertEquals(50, Rating.loss(1000, 1900));
        assertEquals(5, Rating.loss(2000, 1000));
        assertEquals(2, Rating.loss(1000, 2));
        assertEquals(0, Rating.award(Rating.MAX, 1250));
    }

    @Test void adminAdjustmentsClampWithoutIntegerOverflow() {
        assertEquals(Rating.MAX, AdminStatsCommand.Stat.RANKING.updated("add", 1000, Integer.MAX_VALUE));
        assertEquals(0, AdminStatsCommand.Stat.DEATHS.updated("add", 2, -3));
        assertEquals(13, AdminStatsCommand.Stat.KILLSTREAK.updated("set", 5, 13));
    }
}
