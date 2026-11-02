package ai.aisector.listeners;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackupRestoreClaimTest {
    @Test
    void rollbackDistinguishesTwoAttemptsBySameAdmin() {
        ObjectId id = new ObjectId();
        String first = BackupRestoreClaim.rollbackFilter(id, "admin", "attempt1").toBsonDocument().toJson();
        String second = BackupRestoreClaim.rollbackFilter(id, "admin", "attempt2").toBsonDocument().toJson();
        assertNotEquals(first, second);
        assertTrue(first.contains("restore_claim"));
        assertTrue(first.contains("attempt1"));
        assertTrue(first.contains(id.toHexString()));
        assertThrows(IllegalArgumentException.class, () -> BackupRestoreClaim.rollbackFilter(id, "admin", ""));
    }
}
