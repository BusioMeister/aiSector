package ai.aisector.guilds;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildProfileCleanupTest {
    @Test void cleanupRequiresTheObservedAssignmentRevision() {
        var first = new Document("_id", "player").append("guildTag", "G").append("guildMembershipRevision", "old");
        var second = new Document(first).append("guildMembershipRevision", "new");
        assertNotEquals(GuildProfileCleanup.observedAssignment(first).toBsonDocument(),
                GuildProfileCleanup.observedAssignment(second).toBsonDocument());
        assertTrue(GuildProfileCleanup.observedAssignment(first).toBsonDocument().toJson().contains("old"));
    }
    @Test void legacyCleanupCannotMatchAProfileUpgradedByANewWriter() {
        String filter = GuildProfileCleanup.observedAssignment(new Document("_id", "legacy").append("guildTag", "G"))
                .toBsonDocument().toJson();
        assertTrue(filter.contains("$exists"));
        assertTrue(filter.contains("false"));
        assertThrows(IllegalArgumentException.class, () -> GuildProfileCleanup.observedAssignment(new Document()));
    }
}
