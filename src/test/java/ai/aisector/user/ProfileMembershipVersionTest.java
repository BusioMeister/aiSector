package ai.aisector.user;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileMembershipVersionTest {
    @Test void delayedWriterIsBoundToIdentityTagAndObservedRevision() {
        Document old = new Document("_id", "user:p").append("uuid", "p").append("guildTag", "G")
                .append("guildMembershipRevision", "old");
        var filter = ProfileMembershipVersion.observed(old).toBsonDocument();
        assertTrue(filter.toJson().contains("old"));
        assertNotEquals(filter, ProfileMembershipVersion.observed(new Document(old)
                .append("guildMembershipRevision", "new")).toBsonDocument());
        assertNotEquals(filter, ProfileMembershipVersion.observed(new Document(old).append("guildTag", "OTHER")).toBsonDocument());
    }
    @Test void legacyAssignmentMustStillBeUnversioned() {
        String filter = ProfileMembershipVersion.observed(new Document("_id", "legacy").append("uuid", "p"))
                .toBsonDocument().toJson();
        assertTrue(filter.contains("$exists"));
        assertThrows(IllegalArgumentException.class, () -> ProfileMembershipVersion.observed(new Document()));
    }
}
