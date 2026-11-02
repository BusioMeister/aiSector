package ai.aisector.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySerializerValidationTest {
    @Test
    void rejectsCompressedOversizedItemBeforeCallingBukkit() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(bytes)) { gzip.write(new byte[4_194_305]); }
        var error = assertThrows(java.io.IOException.class, () -> InventorySerializer.itemStackFromBase64(
                "v2:" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())));
        assertTrue(error.getMessage().contains("inflated size limit"));
    }

    @Test
    void normalizesHistoricalGetContentsArmorAliasesWithoutOverwritingCanonicalSlots() {
        var parsed = InventorySerializer.parseSerializedInventory("{\"36\":\"old\",\"40\":\"offhand\",\"100\":\"explicit\"}");
        assertEquals(2, parsed.size());
        assertEquals("explicit", parsed.get(100));
        assertEquals("offhand", parsed.get(104));
    }

    @Test
    void rejectsMalformedVersionedBase64BeforeCallingBukkit() {
        assertThrows(java.io.IOException.class, () -> InventorySerializer.itemStackFromBase64("v2:%%%"));
        assertThrows(java.io.IOException.class, () -> InventorySerializer.itemStackFromBase64("v2:"));
    }

    @Test
    void acceptsSupportedInventorySlots() {
        var parsed = InventorySerializer.parseSerializedInventory("{\"0\":\"YQ==\",\"104\":\"Yg==\"}");
        assertEquals(2, parsed.size());
    }

    @Test
    void rejectsInvalidSlotsAndOversizedPayloadsBeforeMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> InventorySerializer.parseSerializedInventory("{\"99\":\"YQ==\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> InventorySerializer.parseSerializedInventory("{\"0\":\"\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> InventorySerializer.parseSerializedInventory("x".repeat(1_048_577)));
    }

    @Test
    void rejectsOversizedEncodedItemBeforeObjectDeserialization() {
        var error = assertThrows(java.io.IOException.class,
                () -> InventorySerializer.itemStackFromBase64("A".repeat(400_000)));
        assertTrue(error.getMessage().contains("encoded size limit"));
    }
}
