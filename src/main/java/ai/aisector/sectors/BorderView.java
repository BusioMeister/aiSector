package ai.aisector.sectors;

/** Geometry of a virtual square with only the selected sector faces near the player. */
record BorderView(BorderType type, double centerX, double centerZ) {
    static final double SIZE = 4096;
    static final double SHOW_DISTANCE = 32;
    private static final double HALF = SIZE / 2;
    private static final double TANGENT_STEP = 512;
    // A visual wall must not stop the client before it sends a crossing movement.
    private static final double CROSSING_BUFFER = 2;
    private static final BorderView AWAY = new BorderView(BorderType.AWAY, 0, 0);

    static BorderView at(Sector sector, double x, double z) {
        if (sector == null || !sector.isInside((int) Math.floor(x), (int) Math.floor(z))) return AWAY;
        double eastEdge = (double) sector.getMaxX() + 1;
        double southEdge = (double) sector.getMaxZ() + 1;
        double west = x - sector.getMinX(), east = eastEdge - x;
        double north = z - sector.getMinZ(), south = southEdge - z;
        boolean nearX = Math.min(west, east) <= SHOW_DISTANCE;
        boolean nearZ = Math.min(north, south) <= SHOW_DISTANCE;
        if (!nearX && !nearZ) return AWAY;
        double centerX = nearX ? (west <= east ? sector.getMinX() - CROSSING_BUFFER + HALF
                : eastEdge + CROSSING_BUFFER - HALF) : snap(x);
        double centerZ = nearZ ? (north <= south ? sector.getMinZ() - CROSSING_BUFFER + HALF
                : southEdge + CROSSING_BUFFER - HALF) : snap(z);
        BorderType type = nearX && nearZ ? BorderType.CORNER
                : nearX ? (west <= east ? BorderType.WEST : BorderType.EAST)
                : (north <= south ? BorderType.NORTH : BorderType.SOUTH);
        return new BorderView(type, centerX, centerZ);
    }

    private static double snap(double coordinate) {
        return Math.rint(coordinate / TANGENT_STEP) * TANGENT_STEP;
    }
}
