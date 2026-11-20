package ai.aisector.ranking;

public final class Rating {
    public static final int INITIAL = 1000;
    public static final int MAX = 1_000_000_000;
    private static final int NO_REWARD_GAP = 300;

    private Rating() {}

    public static int award(int killer, int victim) {
        long gap = (long) victim - killer;
        int proposed = (int) Math.clamp(30 + (gap < 0 ? gap / 10 : gap / 50), 0, 49);
        // Whenever points move, at least one leaves the combined total.
        return Math.min(proposed, Math.min(MAX - killer, Math.max(0, loss(killer, victim) - 1)));
    }

    public static int loss(int killer, int victim) {
        long gap = (long) victim - killer;
        if (gap <= -NO_REWARD_GAP || victim <= 1 || killer >= MAX) return 0;
        return Math.min(victim, (int) Math.clamp(32 + (gap < 0 ? gap / 10 : gap / 50), 0, 50));
    }

    public static int read(Object value, int fallback) {
        return value instanceof Number n ? (int) Math.clamp(n.longValue(), 0, MAX) : fallback;
    }

    public static int increment(int count) {
        return count == Integer.MAX_VALUE ? count : count + 1;
    }
}
