package ai.aisector.utils;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigValidationTest {
    @Test
    void acceptsDefaultAndIsolatedStagingEndpoints() {
        assertDoesNotThrow(() -> RuntimeConfigValidation.validate(Map.of()));
        assertDoesNotThrow(() -> RuntimeConfigValidation.validate(Map.of("redis.port", 6380,
                "mongodb.database", "aisector_staging", "this-sector-name", "Sector1")));
    }

    @Test
    void rejectsMalformedConfigInsteadOfSilentlyFallingBackToProductionDefaults() {
        for (Object value : new Object[]{"6380", 6380.5, 0, 65536, Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(Map.of("redis.port", value)));
        }
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(Map.of("mysql.enabled", "false")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(Map.of("this-sector-name", "sector:bad")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(Map.of("mongodb.database", "bad/db")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(Map.of("mongodb.uri", "http://host")));
    }

    @Test
    void configErrorsDoNotEchoCredentials() {
        var error = assertThrows(IllegalArgumentException.class, () -> RuntimeConfigValidation.validate(
                Map.of("mongodb.uri", "secret-not-a-mongo-uri")));
        assertFalse(error.getMessage().contains("secret"));
    }
}
