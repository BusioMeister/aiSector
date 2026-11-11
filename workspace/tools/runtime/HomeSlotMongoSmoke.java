import ai.aisector.database.MongoDBManager;
import ai.aisector.user.HomeSlotRepository;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import java.util.UUID;

/** Source-launch with the Maven dependency classpath; always restricted to local staging. */
public class HomeSlotMongoSmoke {
    public static void main(String[] args) {
        UUID player = UUID.randomUUID();
        try (MongoDBManager mongo = new MongoDBManager("mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000", "aisector_staging")) {
            HomeSlotRepository first = new HomeSlotRepository(mongo);
            HomeSlotRepository second = new HomeSlotRepository(mongo);
            Document home = new Document("player_uuid", player.toString()).append("home_slot", 1)
                    .append("sector", "Sector1").append("location", new Document("world", "world")
                            .append("x", 1).append("y", 64).append("z", 1).append("yaw", 0).append("pitch", 0));
            try {
                var a = first.createAsync(player, home).handle((v, e) -> e == null);
                var b = second.createAsync(player, home).handle((v, e) -> e == null);
                int successes = (a.join() ? 1 : 0) + (b.join() ? 1 : 0);
                if (successes != 1) throw new AssertionError("Expected exactly one slot creator: " + successes);
                if (mongo.findMany("player_homes", Filters.eq("player_uuid", player.toString())).size() != 1)
                    throw new AssertionError("Duplicate home slot");
                String before = mongo.findOne("player_homes", Filters.eq("player_uuid", player.toString())).getString("_revision");
                second.deleteAsync(player, 1).join();
                first.createAsync(player, home).join();
                String after = mongo.findOne("player_homes", Filters.eq("player_uuid", player.toString())).getString("_revision");
                if (before.equals(after)) throw new AssertionError("Revision reused after recreate");
                second.deleteAsync(player, 1).join();
                if (!mongo.findMany("player_homes", Filters.eq("player_uuid", player.toString())).isEmpty())
                    throw new AssertionError("Home delete failed");
                System.out.println("HOME_MONGO_SMOKE_OK: concurrent creation, single slot, delete/recreate, unique revision");
            } finally {
                // Only records created by this smoke's random player UUID.
                mongo.getCollection("player_homes").deleteMany(Filters.eq("player_uuid", player.toString()));
            }
        }
    }
}
