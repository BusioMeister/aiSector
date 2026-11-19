package ai.aisector.sectors;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

record SpawnLayout(Sector spawn, String spawnWorld, String gameWorld, List<Sector> game) {
    static SpawnLayout create(Map<String, Sector> sectors, String spawnName, String spawnWorld,
                              String gameWorld, List<String> names) {
        Sector spawn = sectors.get(SectorManager.normalizeSectorName(spawnName));
        if (spawn == null || !validWorld(spawnWorld) || !validWorld(gameWorld) || names.size() != 2)
            throw new IllegalArgumentException("Expected a spawn, world names and two game sectors");
        Sector a = sectors.get(SectorManager.normalizeSectorName(names.get(0)));
        Sector b = sectors.get(SectorManager.normalizeSectorName(names.get(1)));
        if (a == null || b == null || a == b || a == spawn || b == spawn || !formsMap(a, b))
            throw new IllegalArgumentException("Game sectors must be two adjoining equal halves of a 1000x1000 map");
        return new SpawnLayout(spawn, spawnWorld, gameWorld, List.of(a, b));
    }

    private static boolean validWorld(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]{1,128}");
    }

    static boolean formsMap(Sector a, Sector b) {
        return a.getWidth() == 500 && b.getWidth() == 500 && a.getDepth() == 1000 && b.getDepth() == 1000
                && a.getMinZ() == b.getMinZ()
                && ((long) a.getMaxX() + 1 == b.getMinX() || (long) b.getMaxX() + 1 == a.getMinX())
                || a.getDepth() == 500 && b.getDepth() == 500 && a.getWidth() == 1000 && b.getWidth() == 1000
                && a.getMinX() == b.getMinX()
                && ((long) a.getMaxZ() + 1 == b.getMinZ() || (long) b.getMaxZ() + 1 == a.getMinZ());
    }

    Sector choose(RandomGenerator random) { return game.get(random.nextInt(game.size())); }

    static int coordinate(RandomGenerator random, int min, int max) {
        // Don't drop the player straight onto the seam or the outer border.
        int inset = Math.min(8, (max - min) / 2);
        return random.nextInt(min + inset, max - inset + 1);
    }
}
