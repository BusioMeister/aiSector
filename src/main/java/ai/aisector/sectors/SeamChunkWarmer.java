package ai.aisector.sectors;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads bounded batches asynchronously, then retains the seam with plugin-owned tickets. */
final class SeamChunkWarmer implements AutoCloseable {
    private static final int CONCURRENT_LOADS = 4;
    private record Load(SeamChunkPlan position, int retries) { }
    private final SectorPlugin plugin;
    private final World world;
    private final ArrayDeque<Load> pending = new ArrayDeque<>();
    private final Set<SeamChunkPlan> owned = new HashSet<>();
    private final int planned;
    private int pinned;
    private int loading;
    private BukkitTask pump;
    private boolean closed;

    SeamChunkWarmer(SectorPlugin plugin, World world, List<SeamChunkPlan> chunks) {
        this.plugin = plugin;
        this.world = world;
        this.planned = chunks.size();
        chunks.forEach(position -> pending.add(new Load(position, 0)));
    }

    void start() {
        if (!pending.isEmpty()) pump = Bukkit.getScheduler().runTaskTimer(plugin, this::pump, 1, 5);
    }

    private void pump() {
        if (pending.isEmpty() && loading == 0) {
            pump.cancel();
            plugin.getLogger().info("Seam chunk tickets ready: " + pinned + "/" + planned + " (" + world.getName() + ")");
            if (pinned < planned) plugin.getLogger().warning("Some seam chunks could not be pinned; crossings still work cold.");
            return;
        }
        for (int i = 0; i < CONCURRENT_LOADS && loading < CONCURRENT_LOADS && !pending.isEmpty(); i++) {
            Load load = pending.removeFirst();
            var pos = load.position();
            loading++;
            try {
                world.getChunkAtAsync(pos.x(), pos.z()).whenComplete((chunk, error) -> {
                    Runnable done = () -> finish(load, error == null && chunk != null);
                    if (Bukkit.isPrimaryThread()) done.run();
                    else BukkitTaskScheduler.execute(plugin, done);
                });
            } catch (RuntimeException error) {
                finish(load, false);
            }
        }
    }

    private void finish(Load load, boolean success) {
        if (closed) return;
        var pos = load.position();
        loading--;
        if (success && world.isChunkLoaded(pos.x(), pos.z())) {
            if (world.addPluginChunkTicket(pos.x(), pos.z(), plugin)) owned.add(pos);
            pinned++;
        } else if (load.retries() < 2) {
            pending.addLast(new Load(pos, load.retries() + 1));
        }
    }

    @Override public void close() {
        closed = true;
        if (pump != null) pump.cancel();
        for (var position : owned) world.removePluginChunkTicket(position.x(), position.z(), plugin);
        owned.clear();
        pending.clear();
    }
}
