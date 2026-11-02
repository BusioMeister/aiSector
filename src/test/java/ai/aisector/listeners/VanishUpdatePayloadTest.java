package ai.aisector.listeners;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishUpdatePayloadTest {
    private static final UUID PLAYER = UUID.fromString("95d3a452-0e1b-4c4d-bf8e-00b9fb69f1ca");

    @Test
    void parsesOnlyKnownActionsAndBoundedIdentity() {
        var valid = VanishUpdatePayload.parse(JsonParser.parseString("{\"uuid\":\"" + PLAYER
                + "\",\"name\":\"Player_1\",\"action\":\"VANISH\"}").getAsJsonObject());
        assertTrue(valid.isPresent());
        assertEquals(PLAYER, valid.orElseThrow().playerUuid());
        assertEquals(VanishUpdatePayload.Action.VANISH, valid.orElseThrow().action());

        assertTrue(VanishUpdatePayload.parse(JsonParser.parseString("{\"uuid\":\"" + PLAYER
                + "\",\"name\":\"Player_1\",\"action\":\"REMOVE\"}").getAsJsonObject()).isEmpty());
        assertTrue(VanishUpdatePayload.parse(JsonParser.parseString(
                "{\"uuid\":1,\"name\":\"Player_1\",\"action\":\"UNVANISH\"}")
                .getAsJsonObject()).isEmpty());
        assertTrue(VanishUpdatePayload.parse(JsonParser.parseString("{\"uuid\":\"" + PLAYER
                + "\",\"name\":\"bad name\",\"action\":\"UNVANISH\"}").getAsJsonObject()).isEmpty());
    }
}
