package ai.aisector.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TpposCoordinatesTest {
    @Test
    void acceptsFiniteCoordinatesInsideWorldHeight() {
        TpposCoordinates parsed = TpposCoordinates.parse("-10.5", "64", "2.25", -64, 320);
        assertEquals(-10.5, parsed.x());
        assertEquals(64, parsed.y());
    }

    @Test
    void rejectsNonFiniteOversizedAndOutOfWorldCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> TpposCoordinates.parse("NaN", "64", "0", -64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> TpposCoordinates.parse("0", "Infinity", "0", -64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> TpposCoordinates.parse("30000001", "64", "0", -64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> TpposCoordinates.parse("0", "320", "0", -64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> TpposCoordinates.parse("0".repeat(33), "64", "0", -64, 320));
    }
}
