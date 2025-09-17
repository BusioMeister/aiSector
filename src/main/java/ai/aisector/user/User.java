package ai.aisector.user;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class User {
    private final UUID uuid;
    private final Map<Integer, Home> homes = new ConcurrentHashMap<>();
    private boolean godMode = false;
    private boolean flying = false;
    private boolean vanished = false;
    private float walkSpeed = 0.2f;
    private float flySpeed = 0.1f;
    // Usunęliśmy pole teleportTarget
    private final Map<Material, Boolean> dropSettings = new ConcurrentHashMap<>();
    private boolean cobblestoneDropEnabled = true;
    private int miningLevel = 1;
    private long miningExperience = 0; // Poprawiono na long
    private final Map<Material, Integer> minedBlocks = new ConcurrentHashMap<>();


    public User(UUID uuid) {
        this.uuid = uuid;
    }

    // Gettery i Settery (bez tych od teleportTarget)
    public Map<Material, Boolean> getDropSettings() {
        return dropSettings;
    }

    public boolean isDropEnabled(Material material) {
        return dropSettings.getOrDefault(material, true);
    }

    public void setDropEnabled(Material material, boolean enabled) {
        this.dropSettings.put(material, enabled);
    }

    public boolean isCobblestoneDropEnabled() {
        return cobblestoneDropEnabled;
    }

    public void setCobblestoneDropEnabled(boolean cobblestoneDropEnabled) {
        this.cobblestoneDropEnabled = cobblestoneDropEnabled;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Map<Integer, Home> getHomes() {
        return homes;
    }

    public void addHome(Home home) {
        this.homes.put(home.getSlot(), home);
    }

    public void removeHome(int slot) {
        this.homes.remove(slot);
    }

    public Home getHome(int slot) {
        return this.homes.get(slot);
    }

    public boolean isGodMode() {
        return godMode;
    }

    public void setGodMode(boolean godMode) {
        this.godMode = godMode;
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public boolean isVanished() {
        return vanished;
    }

    public void setVanished(boolean vanished) {
        this.vanished = vanished;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    public float getFlySpeed() {
        return flySpeed;
    }

    public void setFlySpeed(float flySpeed) {
        this.flySpeed = flySpeed;
    }


    public int getMiningLevel() {
        return miningLevel;
    }

    public Map<Material, Integer> getMinedBlocks() {
        return minedBlocks;
    } // DODANO BRAKUJĄCĄ METODĘ

    public void incrementMinedBlockCount(Material material, int amount) {
        minedBlocks.put(material, getMinedBlockCount(material) + amount);
    }

    public void setMiningLevel(int miningLevel) {
        this.miningLevel = miningLevel;
    }

    public long getMiningExperience() {
        return miningExperience;
    }

    public void setMiningExperience(long miningExperience) {
        this.miningExperience = miningExperience;
    }

    public void addMiningExperience(long amount) {
        this.miningExperience += amount;
    }

    public int getMinedBlockCount(Material material) {
        return minedBlocks.getOrDefault(material, 0);
    }
}