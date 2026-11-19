package ai.aisector.sectors;

/** Places a crossing just inside the destination on the axis actually crossed. */
public final class SectorArrival {
    private static final double INSET = 0.35;

    private SectorArrival() { }

    public static double inside(double from, double to, int minBlock, int maxBlock) {
        double min = minBlock, max = (double) maxBlock + 1;
        double inset = Math.min(INSET, (max - min) / 2);
        if (from < min) return Math.max(to, min + inset);
        if (from >= max) return Math.min(to, max - inset);
        return to;
    }
}
