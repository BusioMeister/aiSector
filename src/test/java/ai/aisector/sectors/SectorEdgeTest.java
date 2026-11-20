package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectorEdgeTest {
    private final Sector west = new Sector("GameWest", -500, -1, -500, 499);
    private final Sector east = new Sector("GameEast", 0, 499, -500, 499);

    private Sector lookup(int x, int z) {
        return west.isInside(x, z) ? west : east.isInside(x, z) ? east : null;
    }

    @Test
    void distinguishesSharedEdgeFromEndOfMapOnBothSides() {
        assertEquals(east, SectorEdge.nearest(west, -.3, 0).neighbor(west, -1, 0, this::lookup));
        assertEquals(west, SectorEdge.nearest(east, .3, 0).neighbor(east, 0, 0, this::lookup));
        assertNull(SectorEdge.nearest(west, -499.8, 0).neighbor(west, -500, 0, this::lookup));
    }

    @Test
    void onlyRepelsOutwardMovementTowardAnUnoccupiedEdge() {
        assertFalse(SectorEdge.repulsion(west, -.9, 0, -.2, 0, this::lookup).active());
        assertEquals(new SectorEdge.Push(1, 0), SectorEdge.repulsion(west, -499, 0, -499.5, 0, this::lookup));
        assertFalse(SectorEdge.repulsion(west, -499.5, 0, -499, 0, this::lookup).active());
        assertEquals(new SectorEdge.Push(1, 1), SectorEdge.repulsion(west, -499, -499, -501, -501, this::lookup));
    }

    @Test
    void adjacentSectorCanCoverJustOnePartOfAnEdge() {
        Sector partial = new Sector("NorthEast", 0, 50, -50, -1);
        assertSame(partial, SectorEdge.EAST.neighbor(west, -1, -10,
                (x, z) -> partial.isInside(x, z) ? partial : null));
        assertNull(SectorEdge.EAST.neighbor(west, -1, 10,
                (x, z) -> partial.isInside(x, z) ? partial : null));
    }

    @Test
    void pushesBackTowardTheCurrentSectorWhenTheSharedCrossingIsBlocked() {
        assertEquals(new SectorEdge.Push(-1, 0), SectorEdge.backInside(west, 0, 0));
        assertEquals(new SectorEdge.Push(1, 0), SectorEdge.backInside(east, -1, 0));
        assertEquals(new SectorEdge.Push(1, -1), SectorEdge.backInside(east, -1, 500));
    }

    @Test
    void cooldownTargetIsFiveBlocksFromTheCrossedEdge() {
        assertEquals(-5.0, SectorEdge.inset(-.2, -1, -500, -1));
        assertEquals(5.0, SectorEdge.inset(.2, 1, 0, 499));
        assertEquals(9.0, SectorEdge.inset(9.0, 1, 0, 499));
        assertEquals(1.0, SectorEdge.inset(.2, 1, 0, 1));
    }
}
