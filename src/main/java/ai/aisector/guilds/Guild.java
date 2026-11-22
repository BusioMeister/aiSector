package ai.aisector.guilds;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Guild {
    private final String tag;
    private String name;
    private UUID owner;
    private UUID deputy;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> mods = new HashSet<>();
    private final Set<UUID> invitations = new HashSet<>();
    private final Set<String> alliedGuilds = new HashSet<>();
    private final Set<String> allyInvitations = new HashSet<>();
    private boolean friendlyFireGuild;
    private boolean friendlyFireAllies;
    private String welcomeMessage;
    private String alertMessage;
    private String homeSector;
    private String homeWorld;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;
    private GuildTerritory territory;
    private final java.util.Map<UUID, String> assignedRanks = new java.util.HashMap<>();
    private final java.util.Map<String, GuildRank> customRanks = new java.util.LinkedHashMap<>();

    GuildTerritory territory() { return territory; }
    void territory(GuildTerritory value) { territory = value; }
    java.util.Map<UUID, String> assignedRanks() { return assignedRanks; }
    java.util.Map<String, GuildRank> customRanks() { return customRanks; }

    GuildRank rank(UUID member) {
        if (member == null || !members.contains(member)) return null;
        if (member.equals(owner)) return GuildRank.LEADER;
        if (member.equals(deputy)) return GuildRank.DEPUTY;
        return customRanks.getOrDefault(assignedRanks.get(member), mods.contains(member) ? GuildRank.MASTER : GuildRank.RECRUIT);
    }

    boolean allows(UUID member, GuildRank.Right right) {
        GuildRank rank = rank(member);
        return rank != null && rank.rights().contains(right);
    }

    void clearRank(UUID member) {
        assignedRanks.remove(member);
        mods.remove(member);
        if (member.equals(deputy)) deputy = null;
    }

    void assignRank(UUID member, GuildRank rank) {
        clearRank(member);
        if (rank == GuildRank.DEPUTY) deputy = member;
        else if (rank == GuildRank.MASTER) mods.add(member);
        else if (rank != GuildRank.RECRUIT) assignedRanks.put(member, rank.id());
    }
    // Identity belongs to this snapshot, not to whichever guild later occupies the same tag.
    private String persistenceGeneration;

    String getPersistenceGeneration() { return persistenceGeneration; }
    void setPersistenceGeneration(String generation) { this.persistenceGeneration = generation; }

    public Guild(String tag) {
        this.tag = tag;
    }

    public Guild(String tag, String name, UUID owner) {
        this.tag = tag;
        this.name = name;
        this.owner = owner;
    }

    public String getTag() { return tag; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public UUID getDeputy() { return deputy; }
    public void setDeputy(UUID deputy) { this.deputy = deputy; }
    public Set<UUID> getMembers() { return members; }
    public String roleOf(UUID player) {
        if (player == null || !members.contains(player)) return null;
        if (player.equals(owner)) return "OWNER";
        return mods.contains(player) ? "MOD" : "MEMBER";
    }
    public Set<UUID> getMods() { return mods; }
    public Set<UUID> getInvitations() { return invitations; }
    public Set<String> getAlliedGuilds() { return alliedGuilds; }
    public Set<String> getAllyInvitations() { return allyInvitations; }
    public boolean isFriendlyFireGuild() { return friendlyFireGuild; }
    public void setFriendlyFireGuild(boolean friendlyFireGuild) { this.friendlyFireGuild = friendlyFireGuild; }
    public boolean isFriendlyFireAllies() { return friendlyFireAllies; }
    public void setFriendlyFireAllies(boolean friendlyFireAllies) { this.friendlyFireAllies = friendlyFireAllies; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
    public String getHomeSector() { return homeSector; }
    public void setHomeSector(String homeSector) { this.homeSector = homeSector; }
    public String getHomeWorld() { return homeWorld; }
    public void setHomeWorld(String homeWorld) { this.homeWorld = homeWorld; }
    public double getHomeX() { return homeX; }
    public void setHomeX(double homeX) { this.homeX = homeX; }
    public double getHomeY() { return homeY; }
    public void setHomeY(double homeY) { this.homeY = homeY; }
    public double getHomeZ() { return homeZ; }
    public void setHomeZ(double homeZ) { this.homeZ = homeZ; }
    public float getHomeYaw() { return homeYaw; }
    public void setHomeYaw(float homeYaw) { this.homeYaw = homeYaw; }
    public float getHomePitch() { return homePitch; }
    public void setHomePitch(float homePitch) { this.homePitch = homePitch; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Guild guild && Objects.equals(tag, guild.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tag);
    }

    @Override
    public String toString() {
        return "Guild{tag='" + tag + "', name='" + name + "', owner=" + owner + '}';
    }
}
