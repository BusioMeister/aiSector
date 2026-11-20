package ai.aisector.sectors;

/** Boat restrictions use the actual sector edges, not the client border's crossing buffer. */
public final class SectorBoatBoundary {
    static final double RESTRICTED_DISTANCE = 5;

    private SectorBoatBoundary() {}

    public static boolean blocksInteraction(Sector sector, double x, double z) {
        return sector.distanceToEdge(x, z) <= RESTRICTED_DISTANCE;
    }

    public static SectorEdge.Push crossing(Sector sector, double x, double z) {
        return SectorEdge.backInside(sector, (int) Math.floor(x), (int) Math.floor(z));
    }
}
