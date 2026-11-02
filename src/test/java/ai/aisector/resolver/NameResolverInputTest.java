package ai.aisector.resolver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameResolverInputTest {
    @Test
    void acceptsOnlyBoundedMinecraftUsernames() {
        assertTrue(NameResolver.isValidUsername("Player_1"));
        assertTrue(NameResolver.isValidUsername("A".repeat(16)));
        assertFalse(NameResolver.isValidUsername(""));
        assertFalse(NameResolver.isValidUsername("bad name"));
        assertFalse(NameResolver.isValidUsername("A".repeat(17)));
        assertFalse(NameResolver.isValidUsername("name\n"));
    }
}
