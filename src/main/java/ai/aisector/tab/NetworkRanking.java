package ai.aisector.tab;

import org.bson.Document;
import ai.aisector.ranking.Rating;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public record NetworkRanking(List<Entry> players, List<Entry> guilds, Map<String, Integer> playerPositions,
                      Map<String, Integer> guildPositions) {
    static final int LIMIT = 20;
    public record Entry(String id, String label, long points, long deaths) {}
    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::points).reversed()
            .thenComparingLong(Entry::deaths).thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id);

    public static NetworkRanking from(Iterable<Document> profiles, Iterable<Document> guilds) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (Document doc : profiles) {
            Entry next = readPlayer(doc);
            if (next != null) byId.merge(next.id(), next, (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        List<Entry> playerList = byId.values().stream().sorted(ORDER).toList();
        Map<String, Entry> guildByTag = new HashMap<>();
        for (Document doc : guilds) {
            if (!(doc.get("tag") instanceof String storedTag)) continue;
            String tag = storedTag.strip().toUpperCase(Locale.ROOT);
            if (tag.isEmpty() || tag.length() > 32 || tag.chars().anyMatch(Character::isISOControl)) continue;
            long points = doc.get("ratingPoints") instanceof Number n ? Math.max(0L, n.longValue()) : 0;
            Entry next = new Entry(tag, tag, points, 0);
            guildByTag.merge(tag, next, (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        List<Entry> guildList = guildByTag.values().stream().sorted(ORDER).toList();
        return new NetworkRanking(playerList.stream().limit(LIMIT).toList(), guildList.stream().limit(LIMIT).toList(),
                ranks(playerList), ranks(guildList));
    }

    public record Movement(int before, int after) {}

    public static Movement comparePositions(Iterable<Document> documents, Entry before, Entry oldVictim,
                                            Entry after, Entry newVictim) {
        Map<String, Integer> counted = new HashMap<>();
        int previous = 1 + (ORDER.compare(oldVictim, before) < 0 ? 1 : 0);
        int current = 1 + (ORDER.compare(newVictim, after) < 0 ? 1 : 0);
        for (Document document : documents) {
            Entry entry = readPlayer(document);
            if (entry == null || entry.id().equals(before.id()) || entry.id().equals(oldVictim.id())) continue;
            int mask = (ORDER.compare(entry, before) < 0 ? 1 : 0) | (ORDER.compare(entry, after) < 0 ? 2 : 0);
            if (mask == 0) continue;
            int earlier = counted.getOrDefault(entry.id(), 0);
            if ((earlier & 1) == 0 && (mask & 1) != 0) previous++;
            if ((earlier & 2) == 0 && (mask & 2) != 0) current++;
            counted.put(entry.id(), earlier | mask);
        }
        return new Movement(previous, current);
    }

    private static Entry readPlayer(Document doc) {
        if (!(doc.get("uuid") instanceof String id) || !(doc.get("name") instanceof String name)
                || !name.matches("[A-Za-z0-9_]{1,16}")) return null;
        try { id = UUID.fromString(id).toString(); }
        catch (IllegalArgumentException invalid) { return null; }
        return new Entry(id, name, Rating.read(doc.get("rating"), Rating.INITIAL), count(doc.get("deaths")));
    }

    private static long count(Object value) {
        return value instanceof Number number ? Math.clamp(number.longValue(), 0, Integer.MAX_VALUE) : 0;
    }

    private static Map<String, Integer> ranks(List<Entry> entries) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) ranks.put(entries.get(i).id(), i + 1);
        return Map.copyOf(ranks);
    }
}
