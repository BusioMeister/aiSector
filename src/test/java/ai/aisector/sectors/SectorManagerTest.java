package ai.aisector.sectors;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorManagerTest {
    @Test
    void normalizesNamesIndependentlyOfTheMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("sector1", SectorManager.normalizeSectorName(" Sector1 "));
            assertEquals("island", SectorManager.normalizeSectorName("ISLAND"));
            assertNull(SectorManager.normalizeSectorName(null));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void transferMetadataKeysAreBoundToThePlayer() {
        UUID uuid = UUID.fromString("95d3a452-0e1b-4c4d-bf8e-00b9fb69f1ca");
        assertTrue(SectorManager.isAllowedTransferKey(uuid, "player:final_teleport_target:" + uuid));
        assertFalse(SectorManager.isAllowedTransferKey(uuid,
                "player:is_respawning:95d3a452-0e1b-4c4d-bf8e-00b9fb69f1cb"));
        assertFalse(SectorManager.isAllowedTransferKey(uuid, "arbitrary:key"));
    }
}
