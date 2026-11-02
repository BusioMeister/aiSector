package ai.aisector.listeners;

final class TransferPayloadValidation {
    private TransferPayloadValidation() { }

    static int boundedInteger(Object value, String field, int minimum, int maximum) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid transfer field: " + field);
        }
        double numericValue = number.doubleValue();
        if (!Double.isFinite(numericValue) || numericValue < minimum || numericValue > maximum
                || numericValue != Math.rint(numericValue)) {
            throw new IllegalArgumentException("Transfer field outside supported bounds: " + field);
        }
        return (int) numericValue;
    }

    static float boundedFloat(Object value, String field, float minimum, float maximum) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid transfer field: " + field);
        }
        float numericValue = number.floatValue();
        if (!Float.isFinite(numericValue) || numericValue < minimum || numericValue > maximum) {
            throw new IllegalArgumentException("Transfer field outside supported bounds: " + field);
        }
        return numericValue;
    }

    static void validateLocation(double x, double y, double z, float yaw, float pitch) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000.0 || Math.abs(z) > 30_000_000.0
                || Math.abs(y) > 4_096.0 || pitch < -90.0f || pitch > 90.0f) {
            throw new IllegalArgumentException("Serialized location is outside supported bounds");
        }
    }
}
