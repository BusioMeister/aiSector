import ai.aisector.database.MongoDBManager;
import ai.aisector.guilds.GuildProfileCleanup;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import java.util.List;
import java.util.UUID;

/** Reproduces a delayed old-guild cleanup after a newer same-tag assignment, against real staging Mongo. */
public class MembershipFenceMongoSmoke {
    public static void main(String[] args) {
        String id = "membership-smoke:" + UUID.randomUUID();
        List<String> ids = List.of(id, id + ":legacy", id + ":unchanged");
        try (var mongo = new MongoDBManager("mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000", "aisector_staging")) {
            var users = mongo.getCollection("users");
            try {
                for (int i = 0; i < ids.size(); i++) {
                    Document initial = new Document("_id", ids.get(i)).append("uuid", UUID.randomUUID().toString())
                            .append("guildTag", "FENCE").append("guildRole", "MEMBER");
                    if (i != 1) initial.put("guildMembershipRevision", "old-assignment");
                    users.insertOne(initial);
                    Document observed = users.find(Filters.eq("_id", ids.get(i))).first();
                    if (i != 2) users.updateOne(Filters.eq("_id", ids.get(i)), new Document("$set",
                            new Document("guildMembershipRevision", "new-assignment").append("guildRole", "OWNER")));
                    long cleared = users.updateOne(GuildProfileCleanup.observedAssignment(observed),
                            new Document("$set", new Document("guildTag", null).append("guildRole", null))).getModifiedCount();
                    if (cleared != (i == 2 ? 1 : 0)) throw new AssertionError("Stale membership cleanup matched: " + i);
                    if (i != 2 && !"OWNER".equals(users.find(Filters.eq("_id", ids.get(i))).first().getString("guildRole")))
                        throw new AssertionError("New assignment was damaged");
                    if (users.updateOne(ai.aisector.user.ProfileMembershipVersion.observed(observed),
                            new Document("$set", new Document("guildTag", "ORPHAN").append("guildRole", "OWNER")))
                            .getMatchedCount() != 0) throw new AssertionError("A delayed membership writer overwrote newer state");
                }
                System.out.println("MEMBERSHIP_FENCE_MONGO_OK: same-tag ABA, legacy upgrade, unchanged cleanup, delayed writer CAS");
            } finally { users.deleteMany(Filters.in("_id", ids)); }
        }
    }
}
