package ai.aisector.user;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

/** The lease is coordination only: this profile-local CAS rejects late writes after lease expiry. */
public final class ProfileMembershipVersion {
    private ProfileMembershipVersion() { }
    public static Bson observed(Document profile) {
        if (profile == null || !profile.containsKey("_id") || !profile.containsKey("uuid"))
            throw new IllegalArgumentException("Missing profile identity");
        return Filters.and(Filters.eq("_id", profile.get("_id")), Filters.eq("uuid", profile.get("uuid")),
                Filters.eq("guildTag", profile.get("guildTag")), profile.containsKey("guildMembershipRevision")
                        ? Filters.eq("guildMembershipRevision", profile.get("guildMembershipRevision"))
                        : Filters.exists("guildMembershipRevision", false));
    }
}
