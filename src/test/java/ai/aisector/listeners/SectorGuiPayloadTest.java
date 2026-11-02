package ai.aisector.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SectorGuiPayloadTest {
    @Test
    void parsesValidOnlineAndOfflineEntries() {
        var entries = SectorGuiPayload.parse("""
                [{"name":"Sector1","isOnline":true,"players":12,"tps":"19.9","ram":512},
                 {"name":"Sector2","isOnline":false}]
                """);
        assertEquals(2, entries.size());
        assertEquals(12, entries.get(0).players());
        assertEquals(false, entries.get(1).online());
    }

    @Test
    void rejectsOversizedCollectionsAndInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> SectorGuiPayload.parse(" ".repeat(65_537)));
        assertThrows(IllegalArgumentException.class, () -> SectorGuiPayload.parse(
                "[{\"name\":\"x\",\"isOnline\":true,\"players\":-1,\"tps\":\"20\",\"ram\":1}]"));
        assertThrows(IllegalArgumentException.class, () -> SectorGuiPayload.parse(
                "[{\"name\":\"x\",\"isOnline\":\"true\"}]"));
        String tooMany = "[" + "{\"name\":\"s\",\"isOnline\":false},".repeat(128)
                + "{\"name\":\"s\",\"isOnline\":false}]";
        assertThrows(IllegalArgumentException.class, () -> SectorGuiPayload.parse(tooMany));
    }
}
