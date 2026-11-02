package ai.aisector.listeners;

import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.UUID;

record VanishUpdatePayload(UUID playerUuid, String playerName, Action action) {
    enum Action { VANISH, UNVANISH }

    static Optional<VanishUpdatePayload> parse(JsonObject data) {
        if (data == null) return Optional.empty();
        try {
            String uuidValue = stringField(data, "uuid");
            String name = stringField(data, "name");
            String actionValue = stringField(data, "action");
            if (!name.matches("[A-Za-z0-9_]{1,16}")) return Optional.empty();
            UUID uuid = UUID.fromString(uuidValue);
            Action action = switch (actionValue) {
                case "VANISH" -> Action.VANISH;
                case "UNVANISH" -> Action.UNVANISH;
                default -> null;
            };
            return action == null ? Optional.empty()
                    : Optional.of(new VanishUpdatePayload(uuid, name, action));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static String stringField(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()
                || !data.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return data.get(key).getAsString();
    }
}
