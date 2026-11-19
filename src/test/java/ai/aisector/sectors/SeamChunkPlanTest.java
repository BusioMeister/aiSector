package ai.aisector.sectors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamChunkPlanTest {
    @Test void coversBothSidesOfTheNegativeToPositiveSeam() {
        var plan = SeamChunkPlan.between(new Sector("West", -500, -1, -500, 499),
                new Sector("East", 0, 499, -500, 499));
        assertEquals(128, plan.size());
        assertTrue(plan.contains(new SeamChunkPlan(-1, -32)));
        assertTrue(plan.contains(new SeamChunkPlan(0, 31)));
        assertTrue(plan.stream().allMatch(chunk -> chunk.x() == -1 || chunk.x() == 0));
    }

    @Test void unalignedSeamDoesNotDuplicateTheSharedChunk() {
        assertEquals(2, SeamChunkPlan.between(new Sector("North", -16, 15, -8, 4),
                new Sector("South", -16, 15, 5, 30)).size());
    }

    @Test void ignoresGapsAndCornerContact() {
        var a = new Sector("A", -16, -1, -16, -1);
        assertTrue(SeamChunkPlan.between(a, new Sector("Gap", 1, 16, -16, -1)).isEmpty());
        assertTrue(SeamChunkPlan.between(a, new Sector("Corner", 0, 16, 0, 16)).isEmpty());
    }

    @Test void refusesAnUnboundedTicketPlan() {
        assertThrows(IllegalArgumentException.class, () -> SeamChunkPlan.between(
                new Sector("West", -16, -1, -5000, 5000), new Sector("East", 0, 15, -5000, 5000)));
    }
}
