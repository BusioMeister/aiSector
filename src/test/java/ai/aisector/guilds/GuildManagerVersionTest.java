package ai.aisector.guilds;

import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildManagerVersionTest {
    private static final CodecRegistry CODEC_REGISTRY = MongoClientSettings.getDefaultCodecRegistry();

    @Test
    void versionZeroFilterMatchesLegacyAndVersionedDocuments() {
        BsonDocument filter = GuildManager.guildVersionFilter(0L)
                .toBsonDocument(BsonDocument.class, CODEC_REGISTRY);

        assertTrue(filter.containsKey("$or"));
        assertEquals(2, filter.getArray("$or").size());
        assertEquals(0L, filter.getArray("$or").get(0).asDocument().getInt64("_version").getValue());
        assertEquals(false, filter.getArray("$or").get(1).asDocument()
                .getDocument("_version").getBoolean("$exists").getValue());
    }

    @Test
    void laterVersionFilterRequiresExactVersion() {
        BsonDocument filter = GuildManager.guildVersionFilter(7L)
                .toBsonDocument(BsonDocument.class, CODEC_REGISTRY);

        assertEquals(7L, filter.getInt64("_version").getValue());
    }

    @Test
    void updateReplacesGuildFieldsAndIncrementsVersionAtomically() {
        Document snapshot = new Document("tag", "ABC").append("name", "Guild");
        BsonDocument update = GuildManager.versionedGuildUpdate(snapshot)
                .toBsonDocument(BsonDocument.class, CODEC_REGISTRY);

        assertEquals(snapshot.toBsonDocument(BsonDocument.class, CODEC_REGISTRY), update.getDocument("$set"));
        assertEquals(1L, update.getDocument("$inc").getInt64("_version").getValue());
    }
}
