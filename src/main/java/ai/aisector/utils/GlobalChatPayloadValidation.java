package ai.aisector.utils;

final class GlobalChatPayloadValidation {
    private GlobalChatPayloadValidation() { }

    static boolean validPacketLength(byte[] payload) {
        return payload != null && payload.length >= 2 && payload.length <= 32_767;
    }

    static boolean validMessage(String message) {
        return message != null && !message.isEmpty() && message.length() <= 4_096;
    }
}
