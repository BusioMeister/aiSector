package ai.aisector.listeners;

import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.UUID;

/** Strict parsers for command/control messages consumed from Redis by Paper. */
final class CommandRedisPayloads {
    private CommandRedisPayloads() { }

    record LocalTeleport(String playerName, String targetName) { }
    record AdminLocationRequest(UUID adminUuid, String targetName) { }

    static Optional<LocalTeleport> localTeleport(JsonObject data) {
        if (data == null) return Optional.empty();
        String player = stringField(data, "playerName");
        String target = stringField(data, "targetName");
        if (!validPlayerName(player) || !validPlayerName(target)) return Optional.empty();
        return Optional.of(new LocalTeleport(player, target));
    }

    static Optional<AdminLocationRequest> adminLocationRequest(JsonObject data) {
        if (data == null) return Optional.empty();
        String uuid = stringField(data, "adminUUID");
        String target = stringField(data, "targetName");
        if (uuid == null || uuid.length() != 36 || !validPlayerName(target)) return Optional.empty();
        try {
            return Optional.of(new AdminLocationRequest(UUID.fromString(uuid), target));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    static Optional<String> weatherType(JsonObject data) {
        String value = data == null ? null : stringField(data, "weatherType");
        return "clear".equals(value) || "rain".equals(value) || "thunder".equals(value)
                ? Optional.of(value) : Optional.empty();
    }

    static Optional<String> timeType(JsonObject data) {
        String value = data == null ? null : stringField(data, "timeType");
        return "day".equals(value) || "night".equals(value) ? Optional.of(value) : Optional.empty();
    }

    private static String stringField(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()
                || !data.getAsJsonPrimitive(key).isString()) return null;
        String value = data.get(key).getAsString();
        return value.isBlank() || value.length() > 128 ? null : value;
    }

    private static boolean validPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }
}
