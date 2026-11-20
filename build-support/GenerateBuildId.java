import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

class GenerateBuildId {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]).resolve("aisector-build.properties");
        byte[] code = new byte[6];
        new SecureRandom().nextBytes(code);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "id=" + HexFormat.of().withUpperCase().formatHex(code) + "\n");
    }
}
