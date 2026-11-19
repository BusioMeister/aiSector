package ai.aisector.listeners;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockedSpawnAdminTpTest {
    @Test
    void requiresOperatorAndExplicitAdminTeleport() {
        JsonObject request = new JsonObject();
        assertFalse(CommandResponseListener.allowsLockedSpawnAdminTransfer(request, true));
        request.addProperty("purpose", "admin-tp");
        assertFalse(CommandResponseListener.allowsLockedSpawnAdminTransfer(request, false));
        assertTrue(CommandResponseListener.allowsLockedSpawnAdminTransfer(request, true));
    }

    @Test
    void authorizedSummonCanSnapshotANonOperator() {
        JsonObject request = new JsonObject();
        request.addProperty("purpose", "admin-summon");
        assertTrue(CommandResponseListener.allowsLockedSpawnAdminTransfer(request, false));
        request.addProperty("purpose", "tpa");
        assertFalse(CommandResponseListener.allowsLockedSpawnAdminTransfer(request, true));
    }
}
