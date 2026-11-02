package ai.aisector.listeners;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/** A rollback must identify the individual attempt, not just the admin who made it. */
final class BackupRestoreClaim {
    static Bson rollbackFilter(ObjectId recordId, String admin, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing restore claim token");
        return Filters.and(Filters.eq("_id", recordId), Filters.eq("restored_by", admin),
                Filters.eq("restore_claim", token));
    }
}
