package ai.aisector.generators;

import org.bukkit.Material;

public enum GeneratorType {
    STONE(Material.STONE, 30),       // 3 sekundy (20 tps)
    OBSIDIAN(Material.OBSIDIAN, 300); // 15 sekund

    private final Material material;
    private final int regenTicks;

    GeneratorType(Material material, int regenTicks) {
        this.material = material;
        this.regenTicks = regenTicks;
    }

    public static GeneratorType fromPersistentName(String value) {
        if (value == null || value.length() > 32) throw new IllegalArgumentException("Invalid generator type");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown generator type", invalid);
        }
    }
    public Material getMaterial() { return material; }
    public int getRegenTicks() { return regenTicks; }
}
