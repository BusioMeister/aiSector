package ai.aisector.guilds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WriteFailureFenceTest {
    @Test
    void invalidatesAllQueuedSnapshotsButNotNewMutationsOrOtherTags() {
        WriteFailureFence fence = new WriteFailureFence();
        fence.invalidateThrough("G", 3);
        assertTrue(fence.rejects("G", 1));
        assertTrue(fence.rejects("G", 3));
        assertFalse(fence.rejects("G", 4));
        assertFalse(fence.rejects("OTHER", 1));
        fence.invalidateThrough("G", 1);
        assertTrue(fence.rejects("G", 3));
    }

    @Test
    void mongoCasIncludesGuildGenerationEvenWhenVersionMatches() {
        String filter = GuildManager.guildIdentityFilter("G", 0, "generation-2").toBsonDocument().toJson();
        assertTrue(filter.contains("generation-2"));
        assertTrue(filter.contains("_generation"));
        assertTrue(filter.contains("_version"));
    }

    @Test
    void legacyIdentityUsesObjectIdAndMissingGenerationFailsClosed() {
        String legacy = GuildManager.guildIdentityFilter("G", 0, "legacy:507f1f77bcf86cd799439011")
                .toBsonDocument().toJson();
        assertTrue(legacy.contains("507f1f77bcf86cd799439011"));
        assertTrue(GuildManager.guildIdentityFilter("G", 0, null).toBsonDocument().toJson()
                .contains("missing-generation"));
    }
}
