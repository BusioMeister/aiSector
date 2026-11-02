package ai.aisector.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicNameValidationTest {
    @Test
    void onlyAcceptsBoundedFileNames() {
        assertTrue(SchematicPaster.isValidSchematicName("gildia_jajko"));
        assertTrue(SchematicPaster.isValidSchematicName("test-1.schem"));
        assertFalse(SchematicPaster.isValidSchematicName("../config"));
        assertFalse(SchematicPaster.isValidSchematicName("folder/test"));
        assertFalse(SchematicPaster.isValidSchematicName("x".repeat(65)));
    }
}
