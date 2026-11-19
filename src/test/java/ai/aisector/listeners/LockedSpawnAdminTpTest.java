package ai.aisector.listeners;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockedSpawnAdminTpTest {
    @Test
    void requiresOperatorAndExplicitAdminTeleport() {
        JsonObject request = new JsonObject();
        assertFalse(CommandResponseListener.allowsLockedSpawnAdminTp(request, true));
        request.addProperty("purpose", "admin-tp");
        assertFalse(CommandResponseListener.allowsLockedSpawnAdminTp(request, false));
        assertTrue(CommandResponseListener.allowsLockedSpawnAdminTp(request, true));
    }
}
