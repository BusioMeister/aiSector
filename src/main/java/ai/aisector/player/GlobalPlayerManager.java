package ai.aisector.player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalPlayerManager {

    // Używamy ConcurrentHashMap, aby bezpiecznie przechowywać listę
    private static final Set<String> globalPlayerList = ConcurrentHashMap.newKeySet();

    public static void updatePlayerList(Set<String> newPlayerList) {
        globalPlayerList.clear();
        globalPlayerList.addAll(newPlayerList);
    }

    public static Set<String> getGlobalPlayerList() {
        return Collections.unmodifiableSet(globalPlayerList);
    }
}