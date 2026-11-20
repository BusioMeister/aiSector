package ai.aisector.tab;

import org.bson.Document;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

record NetworkRanking(List<Entry> players, List<Entry> guilds, Map<String, Integer> playerPositions,
                      Map<String, Integer> guildPositions) {
    static final int LIMIT = 20;
    record Entry(String id, String label, long kills, long deaths) {}
    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::kills).reversed()
            .thenComparingLong(Entry::deaths).thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id);

    static NetworkRanking from(Iterable<Document> profiles, Iterable<Document> guilds) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (Document doc : profiles) {
            if (!(doc.get("uuid") instanceof String id) || !(doc.get("name") instanceof String name)
                    || !name.matches("[A-Za-z0-9_]{1,16}")) continue;
            try { id = UUID.fromString(id).toString(); }
            catch (IllegalArgumentException invalid) { continue; }
            Entry next = new Entry(id, name, count(doc.get("kills")), count(doc.get("deaths")));
            byId.merge(id, next, (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        List<Entry> playerList = byId.values().stream().filter(entry -> entry.kills() > 0).sorted(ORDER).toList();
        Map<String, Entry> guildByTag = new HashMap<>();
        for (Document doc : guilds) {
            if (!(doc.get("tag") instanceof String storedTag)) continue;
            String tag = storedTag.strip().toUpperCase(Locale.ROOT);
            if (tag.isEmpty() || tag.length() > 32 || tag.chars().anyMatch(Character::isISOControl)) continue;
            long kills = 0, deaths = 0;
            if (doc.get("members") instanceof List<?> members) {
                for (Object member : new HashSet<>(members)) {
                    Entry profile = byId.get(member);
                    if (profile == null) continue;
                    kills += profile.kills();
                    deaths += profile.deaths();
                }
            }
            Entry next = new Entry(tag, tag, kills, deaths);
            guildByTag.merge(tag, next, (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        List<Entry> guildList = guildByTag.values().stream().sorted(ORDER).toList();
        return new NetworkRanking(playerList.stream().limit(LIMIT).toList(), guildList.stream().limit(LIMIT).toList(),
                ranks(playerList), ranks(guildList));
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
