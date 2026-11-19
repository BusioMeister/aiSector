package ai.aisector.sectors;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One chunk on either side of each shared gameplay edge, including unaligned edges. */
record SeamChunkPlan(int x, int z) {
    static final int LIMIT = 256;

    static List<SeamChunkPlan> between(Sector a, Sector b) {
        Set<SeamChunkPlan> chunks = new LinkedHashSet<>();
        if (a.getMaxX() + 1 == b.getMinX() || b.getMaxX() + 1 == a.getMinX()) {
            int seam = Math.max(a.getMinX(), b.getMinX());
            add(chunks, seam, Math.max(a.getMinZ(), b.getMinZ()), Math.min(a.getMaxZ(), b.getMaxZ()), true);
        }
        if (a.getMaxZ() + 1 == b.getMinZ() || b.getMaxZ() + 1 == a.getMinZ()) {
            int seam = Math.max(a.getMinZ(), b.getMinZ());
            add(chunks, seam, Math.max(a.getMinX(), b.getMinX()), Math.min(a.getMaxX(), b.getMaxX()), false);
        }
        return List.copyOf(chunks);
    }

    private static void add(Set<SeamChunkPlan> chunks, int seam, int min, int max, boolean alongZ) {
        if (max < min) return;
        int left = (seam - 1) >> 4, right = seam >> 4;
        for (int t = min >> 4; t <= (max >> 4); t++) {
            chunks.add(alongZ ? new SeamChunkPlan(left, t) : new SeamChunkPlan(t, left));
            chunks.add(alongZ ? new SeamChunkPlan(right, t) : new SeamChunkPlan(t, right));
            if (chunks.size() > LIMIT) throw new IllegalArgumentException("Seam exceeds chunk ticket budget " + LIMIT);
        }
    }
}
