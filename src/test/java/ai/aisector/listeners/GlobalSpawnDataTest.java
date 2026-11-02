package ai.aisector.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalSpawnDataTest {
    @Test
    void parsesValidSpawn() {
        GlobalSpawnData spawn = GlobalSpawnData.parse("""
                {"sector":"sector1","x":1.5,"y":64,"z":-2,"yaw":180,"pitch":-45}
                """);
        assertEquals("sector1", spawn.sector());
        assertEquals(1.5, spawn.x());
        assertEquals(-45.0f, spawn.pitch());
    }

    @Test
    void rejectsMalformedOrOutOfBoundsSpawn() {
        assertThrows(RuntimeException.class, () -> GlobalSpawnData.parse("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> GlobalSpawnData.parse(" ".repeat(4_097)));
        assertThrows(IllegalArgumentException.class, () -> GlobalSpawnData.parse(
                "{\"sector\":\"s1\",\"x\":30000001,\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":0}"));
        assertThrows(IllegalArgumentException.class, () -> GlobalSpawnData.parse(
                "{\"sector\":\"s1\",\"x\":0,\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":91}"));
        assertThrows(RuntimeException.class, () -> GlobalSpawnData.parse(
                "{\"sector\":\"s1\",\"x\":\"1\",\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":0}"));
    }
}
