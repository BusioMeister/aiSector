package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldBorderManagerTest {
    private final Sector sector = new Sector("Game", -500, 499, -500, 499);
    private final double half = BorderView.SIZE / 2;

    @Test void onlyTheRelevantFaceIsNearThePlayer() {
        var west = BorderView.at(sector, -490, 0);
        var east = BorderView.at(sector, 490, 0);
        var north = BorderView.at(sector, 0, -490);
        var south = BorderView.at(sector, 0, 490);
        assertEquals(BorderType.WEST, west.type());
        assertEquals(-502, west.centerX() - half);
        assertEquals(BorderType.EAST, east.type());
        assertEquals(502, east.centerX() + half);
        assertEquals(BorderType.NORTH, north.type());
        assertEquals(-502, north.centerZ() - half);
        assertEquals(BorderType.SOUTH, south.type());
        assertEquals(502, south.centerZ() + half);
        assertTrue(half - Math.abs(west.centerZ()) > 1024);
        assertTrue(half - Math.abs(north.centerX()) > 1024);
    }

    @Test void showsTwoAdjacentFacesAtCornerAndClearsAwayOrOutside() {
        var corner = BorderView.at(sector, -499, 499.9);
        assertEquals(BorderType.CORNER, corner.type());
        assertEquals(-502, corner.centerX() - half);
        assertEquals(502, corner.centerZ() + half);
        assertEquals(BorderType.AWAY, BorderView.at(sector, 0, 0).type());
        assertEquals(BorderType.AWAY, BorderView.at(sector, 500, 0).type());
        assertEquals(BorderType.AWAY, BorderView.at(null, 0, 0).type());
    }

    @Test void adjacentStepsDoNotRecenterTheVisibleFace() {
        assertEquals(BorderView.at(sector, -490, 5), BorderView.at(sector, -480, 20));
        assertEquals(0.25, sector.distanceToEdge(499.75, 0));
    }

    @Test void gameWallAppearsAtOneHundredBlocksButSpawnKeepsItsShorterRange() {
        var west = new Sector("GameWest", -500, -1, -500, 499);
        var east = new Sector("GameEast", 0, 499, -500, 499);
        assertEquals(BorderType.AWAY, BorderView.at(west, -100.01, 0).type());
        assertEquals(BorderType.EAST, BorderView.at(west, -100, 0).type());
        assertEquals(BorderType.WEST, BorderView.at(east, 100, 0).type());
        assertEquals(BorderType.AWAY, BorderView.at(east, 100.01, 0).type());
        assertEquals(BorderType.CORNER, BorderView.at(east, 100, -400).type());
        var spawn = new Sector("Spawn", 2000, 2099, 2000, 2099);
        assertEquals(BorderType.AWAY, BorderView.at(spawn, 2050, 2050, BorderView.SPAWN_SHOW_DISTANCE).type());
        assertEquals(BorderType.WEST, BorderView.at(spawn, 2032, 2050, BorderView.SPAWN_SHOW_DISTANCE).type());
    }

    @Test void diagonalCrossingOnlyAdjustsTheCrossedAxisAndRespectsNarrowSectors() {
        assertEquals(.35, SectorArrival.inside(-0.1, 0.25, 0, 499));
        assertEquals(16.75, SectorArrival.inside(16.2, 16.75, -500, 499));
        assertEquals(-.35, SectorArrival.inside(0.1, -0.2, -500, -1));
        assertEquals(.35, SectorArrival.inside(-1, 0.1, 0, 0));
    }
}
