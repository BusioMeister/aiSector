package ai.aisector.sectors.player;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalPlayerManagerTest {
    @Test
    void publishesCompleteImmutableSnapshots() {
        GlobalPlayerManager.updatePlayerList(Set.of("Alice"));
        Set<String> previousSnapshot = GlobalPlayerManager.getGlobalPlayerList();

        GlobalPlayerManager.updatePlayerList(Set.of("Bob", "Charlie"));

        assertEquals(Set.of("Alice"), previousSnapshot);
        assertEquals(Set.of("Bob", "Charlie"), GlobalPlayerManager.getGlobalPlayerList());
    }
}
