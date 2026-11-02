package ai.aisector.generators;

import org.bukkit.Material;

/** Pure guards for delayed generator world mutations and dismantle rewards. */
public final class GeneratorLifecyclePolicy {
    private GeneratorLifecyclePolicy() { }

    public static boolean shouldRegenerate(GeneratorType expected, GeneratorType current,
                                          boolean chunkLoaded, boolean blockIsAir) {
        return expected != null && current == expected && chunkLoaded && blockIsAir;
    }

    public static boolean shouldDeliverDismantle(GeneratorType expected, boolean deleteConfirmed,
                                                 Material currentBlock) {
        return expected != null && deleteConfirmed && currentBlock == expected.getMaterial();
    }
}
