package ai.aisector.sectors;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Loads only a bounded number of candidate chunks; checks terrain on the server thread. */
public final class RandomSurfaceLanding {
    private static final int ATTEMPTS = 12;
    private final SectorPlugin plugin;

    public RandomSurfaceLanding(SectorPlugin plugin) { this.plugin = plugin; }

    public void find(Player player, Location seed, Sector sector, Consumer<Location> result) {
        check(player, seed.getWorld(), sector, seed.getBlockX(), seed.getBlockZ(), ATTEMPTS, result);
    }

    /** Try the first candidate chunk before the backend sends its initial player position. */
    public CompletableFuture<Location> prepare(Location seed, Sector sector) {
        var result = new CompletableFuture<Location>();
        World world = seed.getWorld();
        int x = seed.getBlockX(), z = seed.getBlockZ();
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) -> onMainThread(() -> {
            if (result.isDone()) return;
            if (error != null) result.completeExceptionally(error);
            else result.complete(world.isChunkLoaded(x >> 4, z >> 4) ? findInChunk(world, sector, x, z) : null);
        }));
        return result.orTimeout(1200, TimeUnit.MILLISECONDS);
    }

    private void check(Player player, World world, Sector sector, int x, int z, int remaining,
                       Consumer<Location> result) {
        world.getChunkAtAsync(x >> 4, z >> 4).orTimeout(3, TimeUnit.SECONDS).whenComplete((chunk, error) ->
                onMainThread(() -> {
                    if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
                    Location safe = error == null && world.isChunkLoaded(x >> 4, z >> 4)
                            ? findInChunk(world, sector, x, z) : null;
                    if (safe != null || remaining == 1) {
                        result.accept(safe);
                        return;
                    }
                    var random = ThreadLocalRandom.current();
                    check(player, world, sector, SpawnLayout.coordinate(random, sector.getMinX(), sector.getMaxX()),
                            SpawnLayout.coordinate(random, sector.getMinZ(), sector.getMaxZ()), remaining - 1, result);
                }));
    }

    private void onMainThread(Runnable task) {
        // Paper normally completes this future on the server thread. An extra tick
        // here lets unoccupied chunks unload before the safety check.
        if (Bukkit.isPrimaryThread()) task.run();
        else BukkitTaskScheduler.execute(plugin, task);
    }

    private static Location findInChunk(World world, Sector sector, int x, int z) {
        Location first = insideLandingArea(sector, x, z) ? safeAt(world, x, z) : null;
        if (first != null) return first;
        // Visit every column exactly once with a random offset and an odd stride.
        int start = ThreadLocalRandom.current().nextInt(256);
        int baseX = x & ~15, baseZ = z & ~15;
        for (int i = 0; i < 256; i++) {
            int index = (start + i * 73) & 255;
            int px = baseX + (index & 15), pz = baseZ + (index >> 4);
            if (!insideLandingArea(sector, px, pz) || (px == x && pz == z)) continue;
            Location safe = safeAt(world, px, pz);
            if (safe != null) return safe;
        }
        return null;
    }

    static boolean insideLandingArea(Sector sector, int x, int z) {
        return x >= sector.getMinX() + 8 && x <= sector.getMaxX() - 8
                && z >= sector.getMinZ() + 8 && z <= sector.getMaxZ() - 8;
    }

    private static Location safeAt(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y < world.getMinHeight() || y + 2 >= world.getMaxHeight()) return null;
        var floor = world.getBlockAt(x, y, z);
        if (!safeFloor(floor.getType(), floor.getType().isOccluding()) || floor.isLiquid() || !world.getBlockAt(x, y + 1, z).isEmpty()
                || !world.getBlockAt(x, y + 2, z).isEmpty()) return null;
        Location landing = new Location(world, x + 0.5, y + 1, z + 0.5);
        return world.getWorldBorder().isInside(landing) ? landing : null;
    }

    static boolean safeFloor(Material material, boolean occluding) {
        return occluding && switch (material) {
            case MAGMA_BLOCK, CACTUS, POWDER_SNOW, LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE -> false;
            default -> true;
        };
    }
}
