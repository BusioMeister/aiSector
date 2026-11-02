package ai.aisector.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalChatPayloadValidationTest {
    @Test
    void boundsPluginMessagePacketSize() {
        assertFalse(GlobalChatPayloadValidation.validPacketLength(null));
        assertFalse(GlobalChatPayloadValidation.validPacketLength(new byte[1]));
        assertTrue(GlobalChatPayloadValidation.validPacketLength(new byte[2]));
        assertTrue(GlobalChatPayloadValidation.validPacketLength(new byte[32_767]));
        assertFalse(GlobalChatPayloadValidation.validPacketLength(new byte[32_768]));
    }

    @Test
    void boundsDecodedChatText() {
        assertFalse(GlobalChatPayloadValidation.validMessage(null));
        assertFalse(GlobalChatPayloadValidation.validMessage(""));
        assertTrue(GlobalChatPayloadValidation.validMessage("hello"));
        assertTrue(GlobalChatPayloadValidation.validMessage("x".repeat(4_096)));
        assertFalse(GlobalChatPayloadValidation.validMessage("x".repeat(4_097)));
    }
}
