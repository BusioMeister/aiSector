package ai.aisector.user;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HomeSlotRepositoryTest {
    private final UUID player = UUID.randomUUID();

    private Document home() {
        return new Document("player_uuid", player.toString()).append("home_slot", 1).append("sector", "Sector1")
                .append("location", new Document("world", "world").append("x", 1).append("y", 64)
                        .append("z", 1).append("yaw", 0).append("pitch", 0));
    }

    private static class MemoryStore implements HomeSlotRepository.Store {
        final List<Document> rows = new ArrayList<>();
        Runnable beforeInsert = () -> {};
        Runnable beforeDelete = () -> {};
        public List<Document> find(UUID player, int slot) {
            return rows.stream().map(Document::new).toList();
        }
        public void insert(Document home) {
            beforeInsert.run();
            if (rows.stream().anyMatch(row -> row.get("_id").equals(home.get("_id")))) {
                throw new IllegalStateException("duplicate key");
            }
            rows.add(new Document(home));
        }
        public long deleteObserved(List<Document> observed) {
            beforeDelete.run();
            int before = rows.size();
            rows.removeIf(row -> observed.stream().anyMatch(old -> old.get("_id").equals(row.get("_id"))
                    && java.util.Objects.equals(old.get("_revision"), row.get("_revision"))));
            return before - rows.size();
        }
    }

    @Test
    void twoBackendsContendOnUniqueSlotIdentity() {
        MemoryStore store = new MemoryStore();
        HomeSlotRepository first = new HomeSlotRepository(store);
        HomeSlotRepository second = new HomeSlotRepository(store);
        store.beforeInsert = () -> {
            store.beforeInsert = () -> {};
            second.create(player, home());
        };
        assertThrows(IllegalStateException.class, () -> first.create(player, home()));
        assertEquals(1, store.rows.size());
    }

    @Test
    void staleDeletionCannotDeleteRecreatedSlot() {
        MemoryStore store = new MemoryStore();
        HomeSlotRepository repository = new HomeSlotRepository(store);
        repository.create(player, home());
        String original = store.rows.getFirst().getString("_revision");
        store.beforeDelete = () -> {
            store.rows.clear();
            repository.create(player, home());
        };
        assertThrows(IllegalStateException.class, () -> repository.delete(player, 1));
        assertEquals(1, store.rows.size());
        assertNotEquals(original, store.rows.getFirst().getString("_revision"));
    }

    @Test
    void cleansOnlyObservedLegacyDuplicatesAndAllowsRecreation() {
        MemoryStore store = new MemoryStore();
        store.rows.add(home().append("_id", "legacy1"));
        store.rows.add(home().append("_id", "legacy2"));
        HomeSlotRepository repository = new HomeSlotRepository(store);
        repository.delete(player, 1);
        assertTrue(store.rows.isEmpty());
        repository.create(player, home());
        assertEquals(1, store.rows.size());
        assertThrows(IllegalStateException.class, () -> repository.create(player, home()));
    }

    @Test
    void validatesOwnerAndSlotBeforeMutation() {
        HomeSlotRepository repository = new HomeSlotRepository(new MemoryStore());
        assertThrows(IllegalArgumentException.class, () -> repository.create(UUID.randomUUID(), home()));
        assertThrows(IllegalArgumentException.class, () -> repository.create(player, home().append("home_slot", 6)));
        assertThrows(IllegalArgumentException.class, () -> repository.delete(player, 0));
    }

    @Test
    void staleMenuCannotDeleteANewerHome() {
        MemoryStore store = new MemoryStore();
        HomeSlotRepository repository = new HomeSlotRepository(store);
        repository.create(player, home());
        HomeSlotMenu oldMenu = new HomeSlotMenu(player, store.find(player, 1));
        repository.delete(player, 1);
        repository.create(player, home());
        assertThrows(IllegalStateException.class, () -> repository.deleteObserved(player, 1, oldMenu.observedSlot(1)));
        assertEquals(1, store.rows.size());
        assertFalse(oldMenu.belongsTo(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> repository.deleteObserved(UUID.randomUUID(), 1, oldMenu.observedSlot(1)));
    }

    @Test
    void legacyDeleteFilterRequiresAbsentRevision() {
        String filter = HomeSlotRepository.observedFilter(new Document("_id", "legacy"))
                .toBsonDocument().toJson();
        assertTrue(filter.contains("$exists"));
        assertTrue(filter.contains("false"));
    }
}
