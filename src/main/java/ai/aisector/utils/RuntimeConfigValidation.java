package ai.aisector.utils;

import java.util.Map;

/** Validates endpoint types before Bukkit getters can silently replace malformed values with defaults. */
public final class RuntimeConfigValidation {
    private RuntimeConfigValidation() { }

    public static void validate(Map<String, Object> config) {
        String sector = text(config, "this-sector-name", "Sector1", 64, false);
        if (!sector.matches("[A-Za-z0-9_-]{1,64}")) invalid("this-sector-name");
        text(config, "redis.host", "127.0.0.1", 253, false);
        port(config, "redis.port", 6379);
        text(config, "redis.password", "", 4096, true);
        String uri = text(config, "mongodb.uri", "mongodb://127.0.0.1:27017", 4096, false);
        if (!(uri.startsWith("mongodb://") || uri.startsWith("mongodb+srv://"))) invalid("mongodb.uri");
        String database = text(config, "mongodb.database", "users", 63, false);
        if (database.chars().anyMatch(c -> "/\\.\" $*<>:|?".indexOf(c) >= 0)) invalid("mongodb.database");
        Object mysqlEnabled = config.getOrDefault("mysql.enabled", false);
        if (!(mysqlEnabled instanceof Boolean)) invalid("mysql.enabled");
        if (Boolean.TRUE.equals(mysqlEnabled)) {
            text(config, "mysql.host", "127.0.0.1", 253, false);
            port(config, "mysql.port", 3306);
            String mysqlDb = text(config, "mysql.database", "minecraft", 64, false);
            if (!mysqlDb.matches("[A-Za-z0-9_-]+")) invalid("mysql.database");
            text(config, "mysql.username", "minecraft", 128, false);
            text(config, "mysql.password", "", 4096, true);
        }
    }

    private static String text(Map<String, Object> config, String key, String fallback, int max, boolean empty) {
        Object value = config.getOrDefault(key, fallback);
        if (!(value instanceof String text) || text.length() > max || (!empty && text.isBlank())
                || (value instanceof String textValue && textValue.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid config field: " + key);
        }
        return (String) value;
    }

    private static void port(Map<String, Object> config, String key, int fallback) {
        Object value = config.getOrDefault(key, fallback);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < 1 || number.intValue() > 65535) invalid(key);
    }

    private static void invalid(String key) { throw new IllegalArgumentException("Invalid config field: " + key); }
}
