package ai.aisector.ranking;

public final class Rating {
    public static final int INITIAL = 1000;
    public static final int MAX = 1_000_000_000;

    private Rating() {}

    public static int award(int killer, int victim) {
        int proposed = (int) Math.clamp(30L + ((long) victim - killer) / 10L, 5, 70);
        return Math.min(proposed, MAX - killer);
    }

    public static int loss(int killer, int victim) {
        return Math.min(victim, (int) Math.clamp(20L + ((long) victim - killer) / 30L, 5, 50));
    }

    public static int read(Object value, int fallback) {
        return value instanceof Number n ? (int) Math.clamp(n.longValue(), 0, MAX) : fallback;
    }

    public static int increment(int count) {
        return count == Integer.MAX_VALUE ? count : count + 1;
    }
}
