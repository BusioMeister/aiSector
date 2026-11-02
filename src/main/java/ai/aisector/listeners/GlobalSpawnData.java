package ai.aisector.listeners;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Strict, platform-independent validation for the shared Redis global-spawn record. */
public record GlobalSpawnData(String sector, double x, double y, double z, float yaw, float pitch) {
    public static GlobalSpawnData parse(String json) {
        if (json == null || json.isBlank() || json.length() > 4_096) {
            throw new IllegalArgumentException("Global spawn payload is empty or oversized");
        }
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        String sector = requiredString(data, "sector");
        double x = requiredNumber(data, "x");
        double y = requiredNumber(data, "y");
        double z = requiredNumber(data, "z");
        float yaw = (float) requiredNumber(data, "yaw");
        float pitch = (float) requiredNumber(data, "pitch");
        if (sector.isBlank() || sector.length() > 128 || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000.0 || Math.abs(z) > 30_000_000.0
                || Math.abs(y) > 4_096.0 || pitch < -90.0f || pitch > 90.0f) {
            throw new IllegalArgumentException("Global spawn is outside supported bounds");
        }
        return new GlobalSpawnData(sector, x, y, z, yaw, pitch);
    }

    private static String requiredString(JsonObject data, String field) {
        JsonElement value = data.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid global spawn field: " + field);
        }
        return value.getAsString();
    }

    private static double requiredNumber(JsonObject data, String field) {
        JsonElement value = data.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid global spawn field: " + field);
        }
        return value.getAsDouble();
    }
}
