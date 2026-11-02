package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBorderManagerTest {
    private static final UUID WORLD_ID = UUID.randomUUID();

    @Test
    void suppressesSmallCenterChangesWithinSameWorldAndSector() {
        assertFalse(WorldBorderManager.shouldUpdateBorder(
                WORLD_ID, "Sector1", 100.0, 100.0,
                WORLD_ID, "Sector1", 105.0, 103.0));
    }

    @Test
    void updatesWhenCenterMovesByThreshold() {
        assertTrue(WorldBorderManager.shouldUpdateBorder(
                WORLD_ID, "Sector1", 100.0, 100.0,
                WORLD_ID, "Sector1", 108.0, 100.0));
    }

    @Test
    void updatesWhenPlayerChangesSectorOrWorld() {
        UUID otherWorld = UUID.randomUUID();
        assertTrue(WorldBorderManager.shouldUpdateBorder(
                WORLD_ID, "Sector1", 100.0, 100.0,
                WORLD_ID, "Sector2", 100.0, 100.0));
        assertTrue(WorldBorderManager.shouldUpdateBorder(
                WORLD_ID, "Sector1", 100.0, 100.0,
                otherWorld, "Sector1", 100.0, 100.0));
    }
}
