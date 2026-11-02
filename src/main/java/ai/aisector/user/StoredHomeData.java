package ai.aisector.user;

import org.bson.Document;

/** Validated, platform-independent representation of a persisted home location. */
public record StoredHomeData(int slot, String sector, String world, double x, double y, double z, float yaw, float pitch) {
    public static StoredHomeData parse(Document document) {
        if (document == null) throw new IllegalArgumentException("Home document is missing");
        Object slotValue = document.get("home_slot");
        if (!(slotValue instanceof Number slotNumber)) throw new IllegalArgumentException("Invalid home slot");
        int slot = slotNumber.intValue();
        if (slot < 1 || slot > 5 || slotNumber.doubleValue() != slot) {
            throw new IllegalArgumentException("Home slot outside bounds");
        }
        String sector = document.getString("sector");
        Document location = document.get("location", Document.class);
        if (sector == null || sector.isBlank() || sector.length() > 128 || location == null) {
            throw new IllegalArgumentException("Home metadata is invalid");
        }
        String world = location.getString("world");
        double x = number(location, "x");
        double y = number(location, "y");
        double z = number(location, "z");
        float yaw = (float) number(location, "yaw");
        float pitch = (float) number(location, "pitch");
        validateLocation(world, x, y, z, yaw, pitch);
        return new StoredHomeData(slot, sector, world, x, y, z, yaw, pitch);
    }

    public static void validateLocation(String world, double x, double y, double z, float yaw, float pitch) {
        if (world == null || world.isBlank() || world.length() > 128
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || y < -4_096 || y > 4_096 || pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("Home location outside supported bounds");
        }
    }

    private static double number(Document document, String key) {
        Object value = document.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Invalid home coordinate: " + key);
        return number.doubleValue();
    }
}
