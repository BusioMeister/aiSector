package ai.aisector.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Bounded parser for Redis-fed sector status data shown in the sector GUI. */
record SectorGuiPayload(String name, boolean online, int players, String tps, int ramMb) {
    private static final int MAX_JSON_CHARS = 65_536;
    private static final int MAX_SECTORS = 128;
    private static final int MAX_NAME_CHARS = 64;
    private static final int MAX_PLAYERS = 1_000_000;
    private static final int MAX_RAM_MB = 1_000_000;

    static List<SectorGuiPayload> parse(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Sector GUI payload is empty or oversized");
        }
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) throw new IllegalArgumentException("Sector GUI payload must be an array");
        JsonArray array = root.getAsJsonArray();
        if (array.size() > MAX_SECTORS) throw new IllegalArgumentException("Too many sectors in GUI payload");

        List<SectorGuiPayload> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Sector entry must be an object");
            JsonObject data = element.getAsJsonObject();
            String name = requiredString(data, "name", MAX_NAME_CHARS);
            boolean online = requiredBoolean(data, "isOnline");
            int players = online ? boundedInt(data, "players", 0, MAX_PLAYERS) : 0;
            int ram = online ? boundedInt(data, "ram", 0, MAX_RAM_MB) : 0;
            String tps = online ? requiredString(data, "tps", 32) : "";
            result.add(new SectorGuiPayload(name, online, players, tps, ram));
        }
        return List.copyOf(result);
    }

    private static String requiredString(JsonObject data, String key, int maxLength) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid sector GUI field: " + key);
        }
        String text = value.getAsString();
        if (text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("Sector GUI field outside bounds: " + key);
        }
        return text;
    }

    private static boolean requiredBoolean(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid sector GUI field: " + key);
        }
        return value.getAsBoolean();
    }

    private static int boundedInt(JsonObject data, String key, int min, int max) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid sector GUI field: " + key);
        }
        try {
            int number = value.getAsInt();
            if (number < min || number > max || value.getAsDouble() != number) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Sector GUI field outside bounds: " + key, exception);
        }
    }
}
