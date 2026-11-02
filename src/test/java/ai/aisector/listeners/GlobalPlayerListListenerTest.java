package ai.aisector.listeners;

import ai.aisector.sectors.player.GlobalPlayerManager;
import ai.aisector.shared.RedisChannels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalPlayerListListenerTest {
    @Test
    void ignoresMalformedPresenceAndAcceptsValidSnapshot() {
        GlobalPlayerListListener listener = new GlobalPlayerListListener();
        GlobalPlayerManager.updatePlayerList(java.util.Set.of("Existing"));

        assertDoesNotThrow(() -> listener.onMessage(RedisChannels.GLOBAL_PLAYER_LIST, "not-json"));
        assertEquals(java.util.Set.of("Existing"), GlobalPlayerManager.getGlobalPlayerList());

        listener.onMessage(RedisChannels.GLOBAL_PLAYER_LIST, "[\"Alice\",\"Bob\"]");
        assertEquals(java.util.Set.of("Alice", "Bob"), GlobalPlayerManager.getGlobalPlayerList());

        listener.onMessage(RedisChannels.GLOBAL_PLAYER_LIST, "[\"invalid name\"]");
        assertEquals(java.util.Set.of("Alice", "Bob"), GlobalPlayerManager.getGlobalPlayerList());
        listener.onMessage(RedisChannels.GLOBAL_PLAYER_LIST, "x".repeat(1_048_577));
        assertEquals(java.util.Set.of("Alice", "Bob"), GlobalPlayerManager.getGlobalPlayerList());
    }
}
