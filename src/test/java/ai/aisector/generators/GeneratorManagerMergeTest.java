package ai.aisector.generators;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorManagerMergeTest {
    @Test
    void delayedRegenerationRequiresSameGeneratorLoadedChunkAndAirBlock() {
        assertTrue(GeneratorLifecyclePolicy.shouldRegenerate(
                GeneratorType.STONE, GeneratorType.STONE, true, true));
        assertFalse(GeneratorLifecyclePolicy.shouldRegenerate(
                GeneratorType.STONE, GeneratorType.OBSIDIAN, true, true));
        assertFalse(GeneratorLifecyclePolicy.shouldRegenerate(
                GeneratorType.STONE, GeneratorType.STONE, false, true));
        assertFalse(GeneratorLifecyclePolicy.shouldRegenerate(
                GeneratorType.STONE, GeneratorType.STONE, true, false));
    }

    @Test
    void dismantleRewardRequiresConfirmedDeleteAndOriginalBlockStillPresent() {
        assertTrue(GeneratorLifecyclePolicy.shouldDeliverDismantle(
                GeneratorType.STONE, true, org.bukkit.Material.STONE));
        assertFalse(GeneratorLifecyclePolicy.shouldDeliverDismantle(
                GeneratorType.STONE, false, org.bukkit.Material.STONE));
        assertFalse(GeneratorLifecyclePolicy.shouldDeliverDismantle(
                GeneratorType.STONE, true, org.bukkit.Material.DIRT));
    }

    @Test
    void generatorTypePdcIsAllowlisted() {
        assertEquals(GeneratorType.STONE, GeneratorType.fromPersistentName("STONE"));
        assertEquals(GeneratorType.OBSIDIAN, GeneratorType.fromPersistentName("OBSIDIAN"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GeneratorType.fromPersistentName("UNKNOWN"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GeneratorType.fromPersistentName("X".repeat(33)));
    }

    @Test
    void generatorItemVersionsPreserveLegacyAndRejectUnsupportedOrMismatchedItems() {
        assertEquals(0, GeneratorItemData.parse("STONE", null, org.bukkit.Material.STONE).version());
        assertEquals(1, GeneratorItemData.parse("OBSIDIAN", 1, org.bukkit.Material.OBSIDIAN).version());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GeneratorItemData.parse("STONE", 2, org.bukkit.Material.STONE));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GeneratorItemData.parse("STONE", 1, org.bukkit.Material.OBSIDIAN));
    }

    @Test
    void initialMongoLoadDoesNotOverwriteOrRestoreConcurrentChanges() {
        Location changedLocation = new Location(null, 10, 64, 10);
        Location removedLocation = new Location(null, 20, 64, 20);
        Location untouchedLocation = new Location(null, 30, 64, 30);

        Map<Location, GeneratorType> current = new HashMap<>();
        current.put(changedLocation, GeneratorType.OBSIDIAN);
        Set<Location> modifiedDuringLoad = Set.of(changedLocation, removedLocation);
        Map<Location, GeneratorType> loadedFromMongo = Map.of(
                changedLocation, GeneratorType.STONE,
                removedLocation, GeneratorType.STONE,
                untouchedLocation, GeneratorType.OBSIDIAN);

        int added = GeneratorManager.mergeInitialGenerators(new Object(), current, modifiedDuringLoad,
                loadedFromMongo);

        assertEquals(1, added);
        assertEquals(GeneratorType.OBSIDIAN, current.get(changedLocation));
        assertNull(current.get(removedLocation));
        assertEquals(GeneratorType.OBSIDIAN, current.get(untouchedLocation));
    }

    @Test
    void unregisterCannotRaceBetweenMergeCheckAndInsert() throws InterruptedException {
        Location location = new Location(null, 20, 64, 20);
        Object lock = new Object();
        CountDownLatch mergeReachedInsert = new CountDownLatch(1);
        CountDownLatch allowMerge = new CountDownLatch(1);
        CountDownLatch unregisterStarted = new CountDownLatch(1);
        AtomicBoolean blockFirstInsert = new AtomicBoolean(true);
        AtomicInteger added = new AtomicInteger(-1);
        Map<Location, GeneratorType> current = new ConcurrentHashMap<>() {
            @Override
            public GeneratorType putIfAbsent(Location key, GeneratorType value) {
                if (blockFirstInsert.compareAndSet(true, false)) {
                    mergeReachedInsert.countDown();
                    try {
                        if (!allowMerge.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to continue initial merge");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }
                return super.putIfAbsent(key, value);
            }
        };
        Set<Location> modifiedDuringLoad = ConcurrentHashMap.newKeySet();
        Thread merge = new Thread(() -> added.set(GeneratorManager.mergeInitialGenerators(lock, current,
                modifiedDuringLoad, Map.of(location, GeneratorType.STONE))));
        Thread unregister = new Thread(() -> {
            unregisterStarted.countDown();
            GeneratorManager.unregisterLocal(lock, current, modifiedDuringLoad, location);
        });

        merge.start();
        assertTrue(mergeReachedInsert.await(2, TimeUnit.SECONDS));
        unregister.start();
        assertTrue(unregisterStarted.await(2, TimeUnit.SECONDS));
        allowMerge.countDown();
        merge.join(2_000L);
        unregister.join(2_000L);

        assertFalse(merge.isAlive());
        assertFalse(unregister.isAlive());
        assertEquals(1, added.get());
        assertTrue(modifiedDuringLoad.contains(location));
        assertNull(current.get(location));
    }
}
