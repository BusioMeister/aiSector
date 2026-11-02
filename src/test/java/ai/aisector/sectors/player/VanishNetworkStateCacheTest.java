package ai.aisector.sectors.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VanishNetworkStateCacheTest {
    @org.junit.jupiter.api.Test
    void coldSnapshotRestoresRemoteNamesButNeverOverwritesNewerLiveEvents() {
        VanishNetworkStateCache cache = new VanishNetworkStateCache();
        java.util.UUID remote = java.util.UUID.randomUUID();
        long initial = cache.revision();
        org.junit.jupiter.api.Assertions.assertTrue(cache.replaceSnapshot(initial,
                java.util.Map.of(remote, "Staff"), java.util.Set.of()));
        long reading = cache.revision();
        cache.clear(remote);
        org.junit.jupiter.api.Assertions.assertFalse(cache.replaceSnapshot(reading,
                java.util.Map.of(remote, "Staff"), java.util.Set.of()));
        org.junit.jupiter.api.Assertions.assertTrue(cache.snapshot().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void snapshotIsValidatedBeforeMutationAndExcludesLocalPlayers() {
        VanishNetworkStateCache cache = new VanishNetworkStateCache();
        java.util.UUID local = java.util.UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertTrue(cache.replaceSnapshot(cache.revision(),
                java.util.Map.of(local, "Staff"), java.util.Set.of(local)));
        org.junit.jupiter.api.Assertions.assertTrue(cache.snapshot().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> cache.replaceSnapshot(
                cache.revision(), java.util.Map.of(local, "invalid name"), java.util.Set.of()));
        org.junit.jupiter.api.Assertions.assertTrue(cache.snapshot().isEmpty());
    }

    @Test
    void retainsRemoteVanishesForLaterStaffJoinAndRemovesUnvanishedPlayers() {
        VanishNetworkStateCache cache = new VanishNetworkStateCache();
        UUID vanished = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        cache.setVanished(vanished, "HiddenPlayer");
        cache.setVanished(visible, "ShownPlayer");

        assertEquals("HiddenPlayer", cache.snapshot().get(vanished));
        cache.clear(visible);
        assertFalse(cache.snapshot().containsKey(visible));
        assertEquals(1, cache.snapshot().size());
    }

    @Test
    void remoteVanishRemainsUntilAnExplicitUnvanishUpdate() {
        VanishNetworkStateCache cache = new VanishNetworkStateCache();
        UUID id = UUID.randomUUID();
        cache.setVanished(id, "HiddenPlayer");

        // A backend-local player disconnect does not invoke clear; only an explicit
        // network UNVANISH update is allowed to remove this state.
        assertEquals("HiddenPlayer", cache.snapshot().get(id));
        cache.clear(id);
        assertFalse(cache.snapshot().containsKey(id));
    }

    @Test
    void rejectsInvalidNamesAndNullIdentifiers() {
        VanishNetworkStateCache cache = new VanishNetworkStateCache();
        UUID id = UUID.randomUUID();
        cache.setVanished(id, "not a name");
        cache.setVanished(null, "Name");
        cache.setVanished(id, "X".repeat(17));
        assertEquals(0, cache.snapshot().size());
    }
}
