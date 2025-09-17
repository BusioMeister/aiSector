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
    public Material getMaterial() { return material; }
    public int getRegenTicks() { return regenTicks; }
}
