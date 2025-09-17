package ai.aisector.drop;

import org.bukkit.Material;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class DropConfig {

    public static final class DropSpec {
        private final double baseChancePct; // np. 1.2 => 1.2%
        private final int maxYLevel;
        private final int xpValue;

        public DropSpec(double baseChancePct, int maxYLevel, int xpValue) {
            this.baseChancePct = baseChancePct;
            this.maxYLevel = maxYLevel;
            this.xpValue = xpValue;
        }
        public double baseChancePct() { return baseChancePct; }
        public int maxYLevel() { return maxYLevel; }
        public int xpValue() { return xpValue; }
    }

    private static final EnumMap<Material, DropSpec> SPEC = new EnumMap<>(Material.class);

    static {
        // Jedna, wspólna konfiguracja dla całej wtyczki:
        SPEC.put(Material.DIAMOND,      new DropSpec(1.2, 50, 12));
        SPEC.put(Material.GOLD_INGOT,   new DropSpec(1.6, 80, 8));
        SPEC.put(Material.IRON_INGOT,   new DropSpec(2.0, 80, 4));
        SPEC.put(Material.LAPIS_LAZULI, new DropSpec(1.8, 80, 3));
        SPEC.put(Material.REDSTONE,     new DropSpec(2.6, 80, 4));
    }

    public static Map<Material, DropSpec> getAll() {
        return Collections.unmodifiableMap(SPEC);
    }

    public static DropSpec get(Material mat) {
        return SPEC.get(mat);
    }
}
