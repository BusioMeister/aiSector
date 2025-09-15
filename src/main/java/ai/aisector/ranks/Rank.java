package ai.aisector.ranks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Rank {
    private final int id;
    private final String name;
    private final String prefix;
    private final String chatColor;
    private final int weight;
    private final boolean isDefault;
    private final Integer parentRankId;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();

    public Rank(int id, String name, String prefix, String chatColor, int weight, boolean isDefault, Integer parentRankId) {
        this.id = id;
        this.name = name;
        this.prefix = prefix;
        this.chatColor = chatColor;
        this.weight = weight;
        this.isDefault = isDefault;
        this.parentRankId = parentRankId;
    }

    // Gettery
    public int getId() { return id; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public String getChatColor() { return chatColor; }
    public int getWeight() { return weight; }
    public boolean isDefault() { return isDefault; }
    public Integer getParentRankId() { return parentRankId; }
    public Set<String> getPermissions() { return permissions; }
}