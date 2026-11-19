package ai.aisector.user;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerFlightTest {
    @Test
    void keepsVanillaCreativeFlightWithoutProfileGrant() {
        assertEquals(new PlayerFlight(true, true), PlayerFlight.resolve(GameMode.CREATIVE, false, true));
        assertEquals(new PlayerFlight(true, false), PlayerFlight.resolve(GameMode.CREATIVE, false, false));
    }

    @Test
    void restoresFlyButDoesNotGrantSurvivalFlightFromTheActiveBitAlone() {
        assertEquals(new PlayerFlight(true, true), PlayerFlight.resolve(GameMode.SURVIVAL, true, true));
        assertEquals(new PlayerFlight(true, false), PlayerFlight.resolve(GameMode.SURVIVAL, true, false));
        assertEquals(new PlayerFlight(false, false), PlayerFlight.resolve(GameMode.SURVIVAL, false, true));
        assertEquals(new PlayerFlight(true, true), PlayerFlight.resolve(GameMode.SURVIVAL, true, null));
    }

    @Test
    void spectatorsAlwaysFly() {
        assertEquals(new PlayerFlight(true, true), PlayerFlight.resolve(GameMode.SPECTATOR, false, false));
    }
}
