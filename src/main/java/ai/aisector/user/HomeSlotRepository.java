package ai.aisector.user;

import ai.aisector.database.MongoDBManager;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomic slot creation and observed-generation deletion; no expiring cross-database lock. */
public final class HomeSlotRepository {
    interface Store {
        List<Document> find(UUID player, int slot);
        void insert(Document home);
        long deleteObserved(List<Document> observed);
    }

    private final MongoDBManager mongo;
    private final Store store;

    public HomeSlotRepository(MongoDBManager mongo) {
        this.mongo = mongo;
        this.store = new Store() {
            public List<Document> find(UUID player, int slot) {
                return mongo.findMany("player_homes", Filters.and(
                        Filters.eq("player_uuid", player.toString()), Filters.eq("home_slot", slot)));
            }
            public void insert(Document home) { mongo.insertOne("player_homes", home); }
            public long deleteObserved(List<Document> observed) {
                return mongo.getCollection("player_homes").deleteMany(
                        Filters.or(observed.stream().map(HomeSlotRepository::observedFilter).toList()))
                        .getDeletedCount();
            }
        };
    }

    HomeSlotRepository(Store store) { this.mongo = null; this.store = store; }

    public CompletableFuture<Void> createAsync(UUID player, Document home) {
        Document snapshot = new Document(home);
        snapshot.put("location", new Document(home.get("location", Document.class)));
        return mongo.executeAsync(() -> create(player, snapshot));
    }

    public CompletableFuture<Void> deleteAsync(UUID player, int slot) {
        return mongo.executeAsync(() -> delete(player, slot));
    }

    public CompletableFuture<Void> deleteAsync(UUID player, int slot, List<Document> observed) {
        List<Document> snapshot = observed.stream().map(Document::new).toList();
        return mongo.executeAsync(() -> deleteObserved(player, slot, snapshot));
    }

    void create(UUID player, Document home) {
        StoredHomeData parsed = StoredHomeData.parse(home);
        if (!player.toString().equals(home.getString("player_uuid"))) {
            throw new IllegalArgumentException("Home owner mismatch");
        }
        if (!store.find(player, parsed.slot()).isEmpty()) {
            throw new IllegalStateException("Home slot changed; reopen the menu");
        }
        Document candidate = new Document(home)
                .append("_id", "home:" + player + ":" + parsed.slot())
                .append("_revision", UUID.randomUUID().toString());
        // Mongo's unique _id makes concurrent empty-slot inserts contend on the same identity.
        store.insert(candidate);
    }

    void delete(UUID player, int slot) {
        if (slot < 1 || slot > 5) throw new IllegalArgumentException("Invalid home slot");
        List<Document> observed = store.find(player, slot);
        if (observed.isEmpty()) return;
        deleteObserved(player, slot, observed);
    }

    void deleteObserved(UUID player, int slot, List<Document> observed) {
        if (slot < 1 || slot > 5 || observed.isEmpty()) throw new IllegalArgumentException("No observed slot identity");
        for (Document document : observed) {
            if (!player.toString().equals(document.getString("player_uuid"))
                    || !Integer.valueOf(slot).equals(document.get("home_slot")))
                throw new IllegalArgumentException("Observed home owner/slot mismatch");
        }
        if (store.deleteObserved(observed) != observed.size()) {
            throw new IllegalStateException("Home slot changed during deletion; reopen the menu");
        }
    }

    static Bson observedFilter(Document observed) {
        if (!observed.containsKey("_id")) throw new IllegalArgumentException("Missing home identity");
        return Filters.and(Filters.eq("_id", observed.get("_id")),
                Filters.eq("player_uuid", observed.get("player_uuid")), Filters.eq("home_slot", observed.get("home_slot")),
                observed.containsKey("_revision")
                ? Filters.eq("_revision", observed.get("_revision")) : Filters.exists("_revision", false));
    }
}
