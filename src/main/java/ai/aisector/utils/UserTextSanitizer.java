package ai.aisector.utils;

/** Removes formatting/control characters from untrusted text placed in legacy-formatted chat. */
public final class UserTextSanitizer {
    private UserTextSanitizer() { }

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder safe = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '\u00a7') {
                // Drop the formatting introducer and its code, including hex-style sequences.
                if (index + 1 < input.length()) index++;
                continue;
            }
            if (Character.isISOControl(current)) continue;
            safe.append(current);
        }
        return safe.toString();
    }
}
