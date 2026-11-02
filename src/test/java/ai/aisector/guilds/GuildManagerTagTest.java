package ai.aisector.guilds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildManagerTagTest {
    @Test
    void normalizesTagsIndependentlyOfTheMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("VIP", GuildManager.normalizeTag(" vip "));
            assertEquals("I", GuildManager.normalizeTag("i"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void allianceMembershipIsCaseInsensitive() {
        List<String> allies = List.of("ElItE", "VIP");
        assertTrue(GuildManager.containsTag(allies, "elite"));
        assertTrue(GuildManager.containsTag(allies, "Vip"));
        assertFalse(GuildManager.containsTag(allies, "other"));
        assertFalse(GuildManager.containsTag(allies, null));
    }

    @Test
    void persistentTagsRejectInvalidOrUnboundedKeys() {
        assertTrue(GuildManager.isValidPersistentTag("ELITE"));
        assertFalse(GuildManager.isValidPersistentTag("  "));
        assertFalse(GuildManager.isValidPersistentTag("tag:spoof"));
        assertFalse(GuildManager.isValidPersistentTag("x".repeat(33)));
        assertFalse(GuildManager.isValidPersistentTag("bad\nname"));
    }
}
