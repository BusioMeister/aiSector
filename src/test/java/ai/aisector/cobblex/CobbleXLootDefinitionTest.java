package ai.aisector.cobblex;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CobbleXLootDefinitionTest {
    @Test
    void parsesFiniteChanceAndBoundedIntegerAmounts() {
        CobbleXLootDefinition entry = CobbleXLootDefinition.parse("DIAMOND", 4.5, 1, 3);
        assertEquals(Material.DIAMOND, entry.material());
        assertEquals(4.5, entry.chancePct());
        assertEquals(1, entry.min());
        assertEquals(3, entry.max());
    }

    @Test
    void rejectsNonFiniteChanceInvalidMaterialAndUnsafeAmountRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("DIAMOND", Double.NaN, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("DIAMOND", Double.POSITIVE_INFINITY, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("UNKNOWN_ITEM", 5, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("DIAMOND", 5, 1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("DIAMOND", 5, 3.5, 4));
        assertThrows(IllegalArgumentException.class,
                () -> CobbleXLootDefinition.parse("DIAMOND", 5, 4, 3));
    }
}
