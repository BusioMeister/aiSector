package ai.aisector.commands;

/** Strict numeric bounds for the user-controlled `/tppos` destination. */
record TpposCoordinates(double x, double y, double z) {
    private static final double MAX_HORIZONTAL = 30_000_000.0;

    static TpposCoordinates parse(String xText, String yText, String zText, int minHeight, int maxHeight) {
        if (!boundedInput(xText) || !boundedInput(yText) || !boundedInput(zText)) {
            throw new IllegalArgumentException("Invalid coordinate input");
        }
        final double x;
        final double y;
        final double z;
        try {
            x = Double.parseDouble(xText);
            y = Double.parseDouble(yText);
            z = Double.parseDouble(zText);
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Coordinates must be numbers", malformed);
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL
                || y < minHeight || y >= maxHeight) {
            throw new IllegalArgumentException("Coordinates outside world bounds");
        }
        return new TpposCoordinates(x, y, z);
    }

    private static boolean boundedInput(String value) {
        return value != null && !value.isBlank() && value.length() <= 32;
    }
}
