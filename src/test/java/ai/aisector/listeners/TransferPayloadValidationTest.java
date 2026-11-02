package ai.aisector.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferPayloadValidationTest {
    @Test
    void validatesBoundedNumericFields() {
        assertEquals(8, TransferPayloadValidation.boundedInteger(8L, "slot", 0, 8));
        assertEquals(0.5f, TransferPayloadValidation.boundedFloat(0.5, "exp", 0.0f, 0.999999f));

        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.boundedInteger(8.5, "slot", 0, 8));
        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.boundedInteger(Double.NaN, "level", 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.boundedFloat(Float.POSITIVE_INFINITY, "exp", 0.0f, 1.0f));
    }

    @Test
    void validatesTransferCoordinatesAndOrientation() {
        TransferPayloadValidation.validateLocation(30_000_000, -64, 30_000_000, 180, -90);

        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.validateLocation(Double.NaN, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.validateLocation(30_000_001, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TransferPayloadValidation.validateLocation(0, 64, 0, 0, 90.1f));
    }
}
