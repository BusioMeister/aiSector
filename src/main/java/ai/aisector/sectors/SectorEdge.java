package ai.aisector.sectors;

import java.util.function.BiFunction;

/** A face of an inclusive block rectangle; neighboring sectors may cover only part of a face. */
public enum SectorEdge {
    WEST(1, 0), EAST(-1, 0), NORTH(0, 1), SOUTH(0, -1);

    private final int inwardX;
    private final int inwardZ;

    SectorEdge(int inwardX, int inwardZ) {
        this.inwardX = inwardX;
        this.inwardZ = inwardZ;
    }

    public double distance(Sector sector, double x, double z) {
        return switch (this) {
            case WEST -> x - sector.getMinX();
            case EAST -> sector.getMaxX() + 1.0 - x;
            case NORTH -> z - sector.getMinZ();
            case SOUTH -> sector.getMaxZ() + 1.0 - z;
        };
    }

    public Sector neighbor(Sector sector, int x, int z, BiFunction<Integer, Integer, Sector> lookup) {
        int alongX = Math.clamp(x, sector.getMinX(), sector.getMaxX());
        int alongZ = Math.clamp(z, sector.getMinZ(), sector.getMaxZ());
        return switch (this) {
            case WEST -> lookup.apply(sector.getMinX() - 1, alongZ);
            case EAST -> lookup.apply(sector.getMaxX() + 1, alongZ);
            case NORTH -> lookup.apply(alongX, sector.getMinZ() - 1);
            case SOUTH -> lookup.apply(alongX, sector.getMaxZ() + 1);
        };
    }

    public static SectorEdge nearest(Sector sector, double x, double z) {
        SectorEdge nearest = WEST;
        for (SectorEdge edge : EDGES) {
            if (edge.distance(sector, x, z) < nearest.distance(sector, x, z)) nearest = edge;
        }
        return nearest;
    }

    public static Push repulsion(Sector sector, double fromX, double fromZ, double toX, double toZ,
                                 BiFunction<Integer, Integer, Sector> lookup) {
        int x = 0, z = 0;
        for (SectorEdge edge : EDGES) {
            double before = edge.distance(sector, fromX, fromZ);
            double after = edge.distance(sector, toX, toZ);
            if (after > 0.65 || after >= before) continue;
            if (edge.neighbor(sector, (int) Math.floor(toX), (int) Math.floor(toZ), lookup) != null) continue;
            x += edge.inwardX;
            z += edge.inwardZ;
        }
        return new Push(x, z);
    }

    /** Pushes away from the crossed face, or both faces on a diagonal crossing. */
    public static Push backInside(Sector sector, int targetX, int targetZ) {
        int x = targetX < sector.getMinX() ? 1 : targetX > sector.getMaxX() ? -1 : 0;
        int z = targetZ < sector.getMinZ() ? 1 : targetZ > sector.getMaxZ() ? -1 : 0;
        return new Push(x, z);
    }

    private static final SectorEdge[] EDGES = values();

    public record Push(int x, int z) {
        public boolean active() { return x != 0 || z != 0; }
    }
}
