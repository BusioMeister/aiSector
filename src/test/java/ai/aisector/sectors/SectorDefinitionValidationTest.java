package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorDefinitionValidationTest {
    @Test
    void acceptsDisjointWorldBoundedSectors() {
        assertNull(SectorDefinitionValidation.validate(List.of(
                new Sector("Sector1", -200, 199, -200, 199),
                new Sector("Sector2", -500, -201, -300, 299),
                new Sector("Sector3", 200, 499, -300, 299))));
    }

    @Test
    void rejectsDuplicateNamesOverlapAndUnsafeBounds() {
        assertTrue(SectorDefinitionValidation.validate(List.of(
                new Sector("One", 0, 10, 0, 10), new Sector("one", 20, 30, 20, 30)))
                .contains("duplicate"));
        assertTrue(SectorDefinitionValidation.validate(List.of(
                new Sector("One", 0, 10, 0, 10), new Sector("Two", 10, 20, 5, 15)))
                .contains("overlapping"));
        assertTrue(SectorDefinitionValidation.validate(List.of(
                new Sector("Far", 30_000_001, 30_000_010, 0, 10))).contains("world limits"));
    }
}
