package ai.aisector.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTextSanitizerTest {
    @Test
    void removesLegacyFormattingCodesAndControlCharacters() {
        assertEquals("hello world", UserTextSanitizer.sanitize("hello §cworld\u0000"));
        assertEquals("text", UserTextSanitizer.sanitize("§x§f§ftext"));
        assertEquals("dangling", UserTextSanitizer.sanitize("dangling§"));
    }

    @Test
    void preservesOrdinaryUnicodeAndPunctuation() {
        assertEquals("Zażółć! 😀", UserTextSanitizer.sanitize("Zażółć! 😀"));
    }
}
