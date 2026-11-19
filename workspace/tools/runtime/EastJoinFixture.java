import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import org.bson.Document;

public final class EastJoinFixture {
    public static void main(String[] args) {
        if (!args[1].matches("RwSEast[a-f0-9]{6}")) throw new IllegalArgumentException("Not a test account");
        String uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + args[1])
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        try (var mongo = MongoClients.create("mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000")) {
            var users = mongo.getDatabase("aisector_staging").getCollection("users");
            if ("seed".equals(args[0])) users.insertOne(new Document("_id", "user:" + uuid)
                    .append("uuid", uuid).append("name", args[1]).append("sector", "GameEast"));
            else if ("cleanup".equals(args[0])) users.deleteOne(Filters.and(Filters.eq("uuid", uuid), Filters.eq("name", args[1])));
        }
        System.out.println(uuid);
    }
}
