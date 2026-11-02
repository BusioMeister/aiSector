package ai.aisector.guilds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildDeletionNoticeTest {
    @Test
    void acceptsPersistentWriterLongTagAndNormalizesIt() {
        assertEquals("RUNTIME31", GuildDeletionNotice.parse("{\"tag\":\"runtime31\",\"generation\":\"g1\"}").tag());
        assertEquals("A".repeat(32), GuildDeletionNotice.parse("{\"tag\":\"" + "a".repeat(32) + "\"}").tag());
    }

    @Test
    void rejectsMalformedTypesAndBounds() {
        for (String payload : new String[]{"{}", "[]", "{\"tag\":3}", "{\"tag\":\"a:b\"}",
                "{\"tag\":\"G\",\"generation\":3}", "{\"tag\":\"G\",\"generation\":\"\"}",
                "{\"tag\":\"" + "a".repeat(33) + "\"}"}) {
            assertThrows(RuntimeException.class, () -> GuildDeletionNotice.parse(payload));
        }
    }

    @Test
    void retainsExplicitLegacyNoGenerationContract() {
        assertNull(GuildDeletionNotice.parse("{\"tag\":\"G\"}").generation());
    }
}
