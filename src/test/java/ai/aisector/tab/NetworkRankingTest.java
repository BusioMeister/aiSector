package ai.aisector.tab;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkRankingTest {
    private static String id(int i) { return new UUID(0, i).toString(); }
    private static Document player(int i, int kills, int deaths) {
        return new Document("uuid", id(i)).append("name", "Player" + i).append("kills", kills).append("deaths", deaths);
    }

    @Test void topIsLimitedWithoutLosingPersonalPosition() {
        var profiles = IntStream.rangeClosed(1, 25).mapToObj(i -> player(i, i, 0)).toList();
        var guilds = IntStream.rangeClosed(1, 25).mapToObj(i ->
                new Document("tag", "G" + i).append("members", List.of(id(i)))).toList();
        var ranking = NetworkRanking.from(profiles, guilds);
        assertEquals(20, ranking.players().size());
        assertEquals(20, ranking.guilds().size());
        assertEquals(25, ranking.playerPositions().get(id(1)));
        assertEquals(25, ranking.guildPositions().get("G1"));
        String text = PlainTextComponentSerializer.plainText().serialize(NetworkTabList.renderFooter(ranking));
        assertTrue(text.contains("20. Player6"));
        assertTrue(text.contains("20. G6"));
        assertFalse(text.contains("21."));
    }

    @Test void rankingUsesKillsThenDeathsAndIgnoresDuplicateMembers() {
        var ranking = NetworkRanking.from(List.of(player(1, 5, 2), player(2, 5, 1), player(3, 0, 0)),
                List.of(new Document("tag", "hero").append("members", List.of(id(1), id(1), id(2), id(100)))));
        assertEquals(List.of(id(2), id(1)), ranking.players().stream().map(NetworkRanking.Entry::id).toList());
        assertEquals(10, ranking.guilds().getFirst().kills());
        assertEquals(1, ranking.guildPositions().get("HERO"));
        assertFalse(ranking.playerPositions().containsKey(id(3)));
    }
}
