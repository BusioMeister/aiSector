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
        return new Document("uuid", id(i)).append("name", "Player" + i).append("rating", kills).append("deaths", deaths);
    }

    @Test void topIsLimitedWithoutLosingPersonalPosition() {
        var profiles = IntStream.rangeClosed(1, 25).mapToObj(i -> player(i, i, 0)).toList();
        var guilds = IntStream.rangeClosed(1, 25).mapToObj(i ->
                new Document("tag", "G" + i).append("ratingPoints", i)).toList();
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

    @Test void rankingUsesPointsThenDeathsAndStartsUnratedPlayersAt1000() {
        var ranking = NetworkRanking.from(List.of(player(1, 5, 2), player(2, 5, 1), player(3, 0, 0)),
                List.of(new Document("tag", "hero").append("ratingPoints", 10)));
        assertEquals(List.of(id(2), id(1), id(3)), ranking.players().stream().map(NetworkRanking.Entry::id).toList());
        assertEquals(10, ranking.guilds().getFirst().points());
        assertEquals(1, ranking.guildPositions().get("HERO"));
        var initial = NetworkRanking.from(List.of(new Document("uuid", id(4)).append("name", "NewPlayer")), List.of());
        assertEquals(1000, initial.players().getFirst().points());
    }

    @Test void killMovementUsesBothCombatantsAndDoesNotCountDuplicateProfiles() {
        var before = new NetworkRanking.Entry(id(1), "Player1", 1000, 0);
        var after = new NetworkRanking.Entry(id(1), "Player1", 1055, 0);
        var victim = new NetworkRanking.Entry(id(2), "Player2", 1250, 0);
        var dead = new NetworkRanking.Entry(id(2), "Player2", 1222, 1);
        assertEquals(new NetworkRanking.Movement(3, 2), NetworkRanking.comparePositions(
                List.of(player(1, 1055, 0), player(2, 1222, 1), player(3, 1040, 0), player(3, 1020, 0)),
                before, victim, after, dead));
    }
}
