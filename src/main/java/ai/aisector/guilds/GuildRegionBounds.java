package ai.aisector.guilds;

/** Square, world-local horizontal region around a guild home. */
final class GuildRegionBounds {
    private GuildRegionBounds() { }

    static boolean contains(double x, double z, double centerX, double centerZ, double radius) {
        return Double.isFinite(x) && Double.isFinite(z)
                && Double.isFinite(centerX) && Double.isFinite(centerZ)
                && Double.isFinite(radius) && radius >= 0
                && Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }
}
