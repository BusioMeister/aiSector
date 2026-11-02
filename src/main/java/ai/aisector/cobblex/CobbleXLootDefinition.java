package ai.aisector.cobblex;

import org.bukkit.Material;

import java.math.BigDecimal;

/** Validated, bounded configuration contract for one CobbleX reward. */
record CobbleXLootDefinition(Material material, double chancePct, int min, int max) {
    private static final int MAX_REWARD_AMOUNT = 64;

    static CobbleXLootDefinition parse(Object materialValue, Object chanceValue,
                                       Object minValue, Object maxValue) {
        if (!(materialValue instanceof String name) || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("material must be a non-empty bounded name");
        }
        Material material = Material.matchMaterial(name);
        if (material == null) throw new IllegalArgumentException("unknown material");

        if (!(chanceValue instanceof Number chanceNumber)) {
            throw new IllegalArgumentException("chance must be numeric");
        }
        double chance = chanceNumber.doubleValue();
        if (!Double.isFinite(chance) || chance <= 0.0 || chance > 100.0) {
            throw new IllegalArgumentException("chance must be finite and in (0, 100]");
        }

        int min = exactAmount(minValue, "min");
        int max = exactAmount(maxValue, "max");
        if (max < min) throw new IllegalArgumentException("max must be at least min");
        return new CobbleXLootDefinition(material, chance, min, max);
    }

    private static int exactAmount(Object value, String field) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " must be numeric");
        try {
            int amount = new BigDecimal(number.toString()).intValueExact();
            if (amount < 1 || amount > MAX_REWARD_AMOUNT) throw new IllegalArgumentException(field + " out of range");
            return amount;
        } catch (NumberFormatException | ArithmeticException malformed) {
            throw new IllegalArgumentException(field + " must be a bounded integer", malformed);
        }
    }
}
