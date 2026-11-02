package ai.aisector.user;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredHomeDataTest {
    @Test
    void parsesValidHomeData() {
        Document home = new Document("home_slot", 2).append("sector", "Sector1")
                .append("location", new Document("world", "world").append("x", 1.5)
                        .append("y", 64).append("z", -2).append("yaw", 90).append("pitch", 15));
        StoredHomeData parsed = StoredHomeData.parse(home);
        assertEquals(2, parsed.slot());
        assertEquals(1.5, parsed.x());
    }

    @Test
    void rejectsMalformedAndOutOfRangeHomes() {
        assertThrows(IllegalArgumentException.class, () -> StoredHomeData.parse(new Document()));
        Document invalidPitch = new Document("home_slot", 1).append("sector", "s1")
                .append("location", new Document("world", "world").append("x", 0).append("y", 64)
                        .append("z", 0).append("yaw", 0).append("pitch", 91));
        assertThrows(IllegalArgumentException.class, () -> StoredHomeData.parse(invalidPitch));
        Document invalidSlot = new Document("home_slot", 6).append("sector", "s1")
                .append("location", new Document("world", "world").append("x", 0).append("y", 64)
                        .append("z", 0).append("yaw", 0).append("pitch", 0));
        assertThrows(IllegalArgumentException.class, () -> StoredHomeData.parse(invalidSlot));
    }

    @Test
    void validatesCrossBackendLocationWithoutRequiringLocalWorldObject() {
        StoredHomeData.validateLocation("world_nether", 10, 70, -3, 180, 0);
        assertThrows(IllegalArgumentException.class,
                () -> StoredHomeData.validateLocation("world", Double.NaN, 70, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StoredHomeData.validateLocation("world", 10, 70, 0, 0, 100));
    }
}
