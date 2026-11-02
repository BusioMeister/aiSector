package ai.aisector.utils;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemInputStreamTest {
    private static boolean deserializationRan;
    private static final class UnexpectedPayload implements Serializable {
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            deserializationRan = true;
            input.defaultReadObject();
        }
    }

    @Test void rejectsUnknownClassBeforeItsReadObjectRuns() throws Exception {
        deserializationRan = false;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) { stream.writeObject(new UnexpectedPayload()); }
        assertThrows(IOException.class, () -> InventorySerializer.itemStackFromBase64(Base64.getEncoder().encodeToString(bytes.toByteArray())));
        assertFalse(deserializationRan);
    }

    @Test void deniesUnregisteredAndCustomConfigurationAliasesBeforeBukkitResolution() {
        assertDoesNotThrow(() -> LegacyItemInputStream.validateAlias("ItemMeta"));
        assertThrows(InvalidObjectException.class, () -> LegacyItemInputStream.validateAlias("UnexpectedPluginClass"));
        assertThrows(InvalidObjectException.class, () -> LegacyItemInputStream.validateAlias(null));
        assertThrows(InvalidObjectException.class, () -> LegacyItemInputStream.validateAlias(7));
    }
}
