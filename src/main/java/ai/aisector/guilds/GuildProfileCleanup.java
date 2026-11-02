package ai.aisector.guilds;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

/** Fences cleanup to the profile assignment observed before the guild delete. */
public final class GuildProfileCleanup {
    private GuildProfileCleanup() { }
    public static Bson observedAssignment(Document profile) {
        if (!profile.containsKey("_id") || !(profile.get("guildTag") instanceof String))
            throw new IllegalArgumentException("Missing observed membership identity");
        return Filters.and(Filters.eq("_id", profile.get("_id")), Filters.eq("guildTag", profile.getString("guildTag")),
                profile.containsKey("guildMembershipRevision")
                        ? Filters.eq("guildMembershipRevision", profile.get("guildMembershipRevision"))
                        : Filters.exists("guildMembershipRevision", false));
    }
}
