package ai.aisector.generators;

import org.bukkit.Material;

/** Versioned PDC item contract; absent version denotes the legacy v0 form. */
public record GeneratorItemData(int version, GeneratorType type) {
    public static final int CURRENT_VERSION = 1;

    public static GeneratorItemData parse(String typeName, Integer version, Material material) {
        GeneratorType type = GeneratorType.fromPersistentName(typeName);
        int parsedVersion = version == null ? 0 : version;
        if (parsedVersion < 0 || parsedVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported generator item version");
        }
        if (material != type.getMaterial()) {
            throw new IllegalArgumentException("Generator item material does not match its type");
        }
        return new GeneratorItemData(parsedVersion, type);
    }
}
