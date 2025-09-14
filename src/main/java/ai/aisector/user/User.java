package ai.aisector.user;

import org.bukkit.Location;
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

    public User(UUID uuid) { this.uuid = uuid; }

    // Gettery i Settery (bez tych od teleportTarget)
    public UUID getUuid() { return uuid; }
    public Map<Integer, Home> getHomes() { return homes; }
    public void addHome(Home home) { this.homes.put(home.getSlot(), home); }
    public void removeHome(int slot) { this.homes.remove(slot); }
    public Home getHome(int slot) { return this.homes.get(slot); }
    public boolean isGodMode() { return godMode; }
    public void setGodMode(boolean godMode) { this.godMode = godMode; }
    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    public boolean isVanished() { return vanished; }
    public void setVanished(boolean vanished) { this.vanished = vanished; }
    public float getWalkSpeed() { return walkSpeed; }
    public void setWalkSpeed(float walkSpeed) { this.walkSpeed = walkSpeed; }
    public float getFlySpeed() { return flySpeed; }
    public void setFlySpeed(float flySpeed) { this.flySpeed = flySpeed; }
}