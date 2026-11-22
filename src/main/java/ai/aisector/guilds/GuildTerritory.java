package ai.aisector.guilds;

record GuildTerritory(String world, int x, int z) {
    static final int RADIUS = 30;

    boolean contains(String world, int x, int z) {
        return this.world.equalsIgnoreCase(world)
                && Math.abs((long) x - this.x) <= RADIUS && Math.abs((long) z - this.z) <= RADIUS;
    }

    boolean overlaps(GuildTerritory other) {
        return world.equalsIgnoreCase(other.world)
                && Math.abs((long) x - other.x) <= 2 * RADIUS && Math.abs((long) z - other.z) <= 2 * RADIUS;
    }
}
