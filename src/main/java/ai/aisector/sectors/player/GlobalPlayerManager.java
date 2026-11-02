package ai.aisector.sectors.player;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class GlobalPlayerManager {

    private static final AtomicReference<Set<String>> globalPlayerList = new AtomicReference<>(Set.of());

    public static void updatePlayerList(Set<String> newPlayerList) {
        if (newPlayerList == null) return;
        Set<String> snapshot = new HashSet<>(newPlayerList);
        snapshot.removeIf(java.util.Objects::isNull);
        globalPlayerList.set(Set.copyOf(snapshot));
    }

    public static Set<String> getGlobalPlayerList() {
        return globalPlayerList.get();
    }
}
