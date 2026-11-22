package ai.aisector.generators;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeneratorManager {

    private final SectorPlugin plugin;
    private final Map<Location, GeneratorType> generators = new ConcurrentHashMap<>();
    private final java.util.Set<Location> modifiedLocations = ConcurrentHashMap.newKeySet();
    private final Map<Location, BukkitRunnable> regenerationTasks = new ConcurrentHashMap<>();
    private final java.util.Set<Location> pendingRemovals = ConcurrentHashMap.newKeySet();
    private final Map<Location, Long> locationGenerations = new ConcurrentHashMap<>();
    private final Object generatorStateLock = new Object();
    private final AtomicBoolean loadStarted = new AtomicBoolean();
    private volatile boolean initialLoadComplete;
    private final ExecutorService mongoWriter = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2048), runnable -> {
                Thread thread = new Thread(runnable, "aiSector-generator-mongo-write");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public GeneratorManager(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    // Rejestracja w pamięci; trwały zapis jest wykonywany poza głównym wątkiem.
    public void register(Location loc, GeneratorType type) {
        Location key = loc.toBlockLocation();
        locationGenerations.merge(key, 1L, Long::sum);
        cancelRegen(key);
        registerLocal(generatorStateLock, generators, modifiedLocations, key, type);

        MongoCollection<Document> col = plugin.getMongoDBManager()
                .getCollection("generators")
                .withWriteConcern(WriteConcern.MAJORITY);

        Document filter = new Document("world", key.getWorld().getName())
                .append("x", key.getBlockX())
                .append("y", key.getBlockY())
                .append("z", key.getBlockZ());

        Document data = new Document(filter).append("type", type.name());
        enqueueMongoWrite("persist generator at " + key,
                () -> col.updateOne(filter, new Document("$set", data), new UpdateOptions().upsert(true)));
    }

    /** Removes a generator only after Mongo confirms the durable delete. */
    public CompletableFuture<Boolean> unregisterAsync(Location loc) {
        Location key = loc.toBlockLocation();
        if (!pendingRemovals.add(key)) return CompletableFuture.completedFuture(false);
        GeneratorType expectedType = generators.get(key);
        long expectedGeneration = locationGenerations.getOrDefault(key, 0L);
        if (expectedType == null) {
            pendingRemovals.remove(key);
            return CompletableFuture.completedFuture(false);
        }

        MongoCollection<Document> col = plugin.getMongoDBManager()
                .getCollection("generators")
                .withWriteConcern(WriteConcern.MAJORITY);

        Document filter = new Document("world", key.getWorld().getName())
                .append("x", key.getBlockX())
                .append("y", key.getBlockY())
                .append("z", key.getBlockZ());

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            mongoWriter.execute(() -> {
                try {
                    col.deleteOne(filter);
                    boolean scheduled = BukkitTaskScheduler.execute(plugin, () -> {
                        if (generators.get(key) == expectedType
                                && locationGenerations.getOrDefault(key, 0L) == expectedGeneration) {
                            unregisterLocal(generatorStateLock, generators, modifiedLocations, key);
                            cancelRegen(key);
                            result.complete(true);
                        } else {
                            result.complete(false);
                        }
                        pendingRemovals.remove(key);
                    });
                    if (!scheduled) {
                        pendingRemovals.remove(key);
                        result.complete(false);
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not delete generator at " + key + ": " + exception.getMessage());
                    pendingRemovals.remove(key);
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            pendingRemovals.remove(key);
            result.completeExceptionally(exception);
        }
        return result;
    }

    private void enqueueMongoWrite(String operation, Runnable write) {
        try {
            mongoWriter.execute(() -> {
                try {
                    write.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not " + operation + ": " + exception.getMessage());
                }
            });
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().severe("Could not queue " + operation + ": " + exception.getMessage());
        }
    }

    public boolean isGenerator(Location loc) {
        return generators.containsKey(loc.toBlockLocation());
    }

    public GeneratorType getType(Location loc) {
        return generators.get(loc.toBlockLocation());
    }

    // Odnowa bloku po czasie
    public void scheduleRegen(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        GeneratorType type = generators.get(loc);
        if (type == null) return;

        BukkitRunnable regeneration = new BukkitRunnable() {
            @Override
            public void run() {
                if (generators.get(loc) != type) {
                    finish();
                    return;
                }
                World world = loc.getWorld();
                if (world == null) {
                    finish();
                    return;
                }
                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return;
                }
                Block current = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if (GeneratorLifecyclePolicy.shouldRegenerate(type, generators.get(loc), true,
                        current.getType().isAir())) {
                    current.setType(type.getMaterial());
                }
                finish();
            }

            private void finish() {
                regenerationTasks.remove(loc, this);
                cancel();
            }
        };
        if (regenerationTasks.putIfAbsent(loc, regeneration) == null) {
            try {
                regeneration.runTaskTimer(plugin, type.getRegenTicks(), 20L);
            } catch (RuntimeException schedulingFailure) {
                regenerationTasks.remove(loc, regeneration);
                plugin.getLogger().warning("Could not schedule generator regeneration at " + loc + ": "
                        + schedulingFailure.getMessage());
            }
        }
    }

    private void cancelRegen(Location loc) {
        BukkitRunnable task = regenerationTasks.remove(loc);
        if (task != null) task.cancel();
    }

    // Wczytanie Mongo poza głównym wątkiem; struktury Bukkit aktualizujemy na głównym.
    public void loadAll() {
        if (!loadStarted.compareAndSet(false, true)) return;
        plugin.getMongoDBManager().findManyAsync("generators", new Document())
                .whenComplete((documents, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().severe("Could not load generators from MongoDB: " + error.getMessage());
                        return;
                    }

                    int skippedNoWorld = 0, skippedBad = 0;
                    Map<Location, GeneratorType> loadedFromMongo = new java.util.HashMap<>();
                    for (Document document : documents == null ? java.util.List.<Document>of() : documents) {
                        try {
                        String worldName = document.getString("world");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) { skippedNoWorld++; continue; }

                        Integer x = document.getInteger("x");
                        Integer y = document.getInteger("y");
                        Integer z = document.getInteger("z");
                        String typeName = document.getString("type");
                        if (x == null || y == null || z == null || typeName == null) { skippedBad++; continue; }

                            GeneratorType type = GeneratorType.valueOf(typeName);
                            Location location = new Location(world, x, y, z).toBlockLocation();
                            loadedFromMongo.putIfAbsent(location, type);
                        } catch (RuntimeException exception) {
                            skippedBad++;
                        }
                    }
                    int loaded = mergeInitialGenerators(generatorStateLock, generators, modifiedLocations,
                            loadedFromMongo);
                    plugin.getLogger().info("[Generators] Loaded " + loaded + " (skipped no-world="
                            + skippedNoWorld + ", bad=" + skippedBad + ").");
                    initialLoadComplete = true;
                }));
    }

    static void registerLocal(Object lock, Map<Location, GeneratorType> current,
                              java.util.Set<Location> modifiedDuringLoad, Location location,
                              GeneratorType type) {
        synchronized (lock) {
            modifiedDuringLoad.add(location);
            current.put(location, type);
        }
    }

    static void unregisterLocal(Object lock, Map<Location, GeneratorType> current,
                                java.util.Set<Location> modifiedDuringLoad, Location location) {
        synchronized (lock) {
            modifiedDuringLoad.add(location);
            current.remove(location);
        }
    }

    static int mergeInitialGenerators(Object lock, Map<Location, GeneratorType> current,
                                      java.util.Set<Location> modifiedDuringLoad,
                                      Map<Location, GeneratorType> loadedFromMongo) {
        synchronized (lock) {
            int added = 0;
            for (Map.Entry<Location, GeneratorType> entry : loadedFromMongo.entrySet()) {
                Location location = entry.getKey();
                if (!modifiedDuringLoad.contains(location)
                        && current.putIfAbsent(location, entry.getValue()) == null) {
                    added++;
                }
            }
            return added;
        }
    }

    public CompletableFuture<Void> checkpoint() {
        return CompletableFuture.runAsync(() -> {
            if (!initialLoadComplete || !pendingRemovals.isEmpty()) throw new IllegalStateException("Generator state is not ready");
        }, mongoWriter);
    }

    // Zakończ oczekujące zapisy; kolekcja nie jest przepisywana.
    public void saveAll() {
        mongoWriter.shutdown();
        try {
            if (!mongoWriter.awaitTermination(35, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for generator writes before final snapshot.");
                mongoWriter.shutdownNow();
                if (!mongoWriter.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("Skipping final generator snapshot because writes are still running.");
                    return;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            mongoWriter.shutdownNow();
            plugin.getLogger().severe("Skipping final generator snapshot because shutdown was interrupted.");
            return;
        }

        if (!initialLoadComplete) {
            plugin.getLogger().warning("Skipping destructive generator snapshot because the initial Mongo load "
                    + "did not complete; queued generator changes were already flushed.");
            return;
        }

        // Every mutation is already queued as an ordered, majority-acknowledged write.
        // Do not delete/rebuild the collection here: a failed insert after deleteMany
        // would turn an ordinary shutdown error into total persistent data loss.
        plugin.getLogger().info("[Generators] Mutation queue flushed; no destructive shutdown snapshot required.");
    }
}
