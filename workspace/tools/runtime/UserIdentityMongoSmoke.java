import ai.aisector.database.MongoDBManager;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UserIdentityMongoSmoke {
    public static void main(String[] args) {
        String uuid = UUID.randomUUID().toString();
        try (var mongo = new MongoDBManager("mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000", "aisector_staging")) {
            try {
                CompletableFuture<?>[] writes = new CompletableFuture<?>[24];
                for (int i = 0; i < writes.length; i++) {
                    int index = i;
                    writes[i] = CompletableFuture.runAsync(() -> mongo.updateOneByUuid("users", uuid,
                            new Document("$set", new Document("fixtureField" + index, index))));
                }
                CompletableFuture.allOf(writes).join();
                var profiles = mongo.findMany("users", Filters.eq("uuid", uuid));
                if (profiles.size() != 1) throw new AssertionError("Concurrent profile creation produced duplicates");
                if (!profiles.getFirst().getString("_id").equals("user:" + uuid)) throw new AssertionError("Noncanonical new profile identity");
                for (int i = 0; i < writes.length; i++)
                    if (profiles.getFirst().getInteger("fixtureField" + i, -1) != i) throw new AssertionError("Lost profile field " + i);
                System.out.println("USER_IDENTITY_MONGO_OK: 24 concurrent creators, one profile, no lost fields");
            } finally { mongo.getCollection("users").deleteMany(Filters.eq("uuid", uuid)); }
        }
    }
}
