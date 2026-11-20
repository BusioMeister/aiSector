package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectorBoatBoundaryTest {
    private final Sector west = new Sector("GameWest", -500, -1, -500, 499);
    private final Sector east = new Sector("GameEast", 0, 499, -500, 499);

    @Test
    void blocksInteractionWithinFiveBlocksOfSharedAndOuterEdges() {
        assertFalse(SectorBoatBoundary.blocksInteraction(west, -5.01, 0));
        assertTrue(SectorBoatBoundary.blocksInteraction(west, -5, 0));
        assertTrue(SectorBoatBoundary.blocksInteraction(east, 5, 0));
        assertFalse(SectorBoatBoundary.blocksInteraction(east, 5.01, 0));
        assertTrue(SectorBoatBoundary.blocksInteraction(east, 495, 0));
        assertTrue(SectorBoatBoundary.blocksInteraction(west, 1, 0));
    }

    @Test
    void destroysOnlyOnCrossingAndPushesTowardTheLocalSector() {
        assertFalse(SectorBoatBoundary.crossing(west, -.001, 0).active());
        assertFalse(SectorBoatBoundary.crossing(east, 0, 0).active());
        assertEquals(new SectorEdge.Push(-1, 0), SectorBoatBoundary.crossing(west, 0, 0));
        assertEquals(new SectorEdge.Push(1, 0), SectorBoatBoundary.crossing(east, -.001, 0));
        assertEquals(new SectorEdge.Push(-1, 1), SectorBoatBoundary.crossing(east, 501, -501));
    }
}
