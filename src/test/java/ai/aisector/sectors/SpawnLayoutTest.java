package ai.aisector.sectors;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;

import static org.junit.jupiter.api.Assertions.*;

class SpawnLayoutTest {
    private final Sector west = new Sector("West", -500, -1, -500, 499);
    private final Sector east = new Sector("East", 0, 499, -500, 499);
    private final Sector spawn = new Sector("Spawn", 2000, 2099, 2000, 2099);

    @Test void acceptsExactlyTwoEqualAdjoiningHalvesAndSeparateSpawn() {
        var sectors = Map.of("west", west, "east", east, "spawn", spawn);
        var layout = SpawnLayout.create(sectors, "Spawn", "spawn", "world", List.of("West", "East"));
        assertSame(spawn, layout.spawn());
        assertThrows(IllegalArgumentException.class,
                () -> SpawnLayout.create(sectors, "Spawn", "spawn", "world", List.of("East", "East")));
        assertFalse(SpawnLayout.formsMap(west, new Sector("Gap", 1, 500, -500, 499)));
        assertFalse(SpawnLayout.formsMap(west, new Sector("Shift", 0, 499, -499, 500)));
    }

    @Test void bothHalvesAreReachableWithoutSelectingTheSeamOrOutside() {
        var layout = new SpawnLayout(spawn, "spawn", "world", List.of(west, east));
        var random = new Random(7);
        var selected = new java.util.HashSet<Sector>();
        for (int i = 0; i < 100; i++) {
            Sector target = layout.choose(random);
            selected.add(target);
            int x = SpawnLayout.coordinate(random, target.getMinX(), target.getMaxX());
            assertTrue(x >= target.getMinX() + 8 && x <= target.getMaxX() - 8);
        }
        assertEquals(2, selected.size());
    }

    @Test void rejectsLiquidAndHazardousFloor() {
        assertTrue(RandomSurfaceLanding.safeFloor(Material.STONE, true));
        assertFalse(RandomSurfaceLanding.safeFloor(Material.WATER, false));
        assertFalse(RandomSurfaceLanding.safeFloor(Material.MAGMA_BLOCK, true));
        assertFalse(RandomSurfaceLanding.safeFloor(Material.CACTUS, false));
    }

    @Test void nearbyLandingSearchNeverCrossesTheSeamOrOuterMargin() {
        assertTrue(RandomSurfaceLanding.insideLandingArea(east, 8, -492));
        assertTrue(RandomSurfaceLanding.insideLandingArea(east, 491, 491));
        assertFalse(RandomSurfaceLanding.insideLandingArea(east, 7, 0));
        assertFalse(RandomSurfaceLanding.insideLandingArea(east, -10, 0));
        assertFalse(RandomSurfaceLanding.insideLandingArea(east, 10, 492));
    }
}
