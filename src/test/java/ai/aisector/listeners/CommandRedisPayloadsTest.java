package ai.aisector.listeners;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRedisPayloadsTest {
    @Test
    void validatesLocalTeleportIdentityFields() {
        var valid = CommandRedisPayloads.localTeleport(JsonParser.parseString(
                "{\"playerName\":\"Admin_1\",\"targetName\":\"Player\"}").getAsJsonObject());
        assertTrue(valid.isPresent());
        assertEquals("Player", valid.orElseThrow().targetName());
        assertTrue(CommandRedisPayloads.localTeleport(JsonParser.parseString(
                "{\"playerName\":1,\"targetName\":\"Player\"}").getAsJsonObject()).isEmpty());
        assertTrue(CommandRedisPayloads.localTeleport(JsonParser.parseString(
                "{\"playerName\":\"Admin\",\"targetName\":\"invalid name\"}").getAsJsonObject()).isEmpty());
    }

    @Test
    void validatesAdminUuidAndWorldControlEnums() {
        assertTrue(CommandRedisPayloads.adminLocationRequest(JsonParser.parseString(
                "{\"adminUUID\":\"95d3a452-0e1b-4c4d-bf8e-00b9fb69f1ca\",\"targetName\":\"Player\"}")
                .getAsJsonObject()).isPresent());
        assertTrue(CommandRedisPayloads.adminLocationRequest(JsonParser.parseString(
                "{\"adminUUID\":\"bad\",\"targetName\":\"Player\"}").getAsJsonObject()).isEmpty());
        assertEquals("thunder", CommandRedisPayloads.weatherType(JsonParser.parseString(
                "{\"weatherType\":\"thunder\"}").getAsJsonObject()).orElseThrow());
        assertTrue(CommandRedisPayloads.weatherType(JsonParser.parseString(
                "{\"weatherType\":\"snow\"}").getAsJsonObject()).isEmpty());
        assertEquals("night", CommandRedisPayloads.timeType(JsonParser.parseString(
                "{\"timeType\":\"night\"}").getAsJsonObject()).orElseThrow());
        assertTrue(CommandRedisPayloads.timeType(JsonParser.parseString(
                "{\"timeType\":true}").getAsJsonObject()).isEmpty());
    }
}
