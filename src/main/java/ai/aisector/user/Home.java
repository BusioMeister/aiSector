package ai.aisector.user;
import org.bukkit.Location;

public class Home {
    private final int slot;
    private final String sector;
    private final Location location;

    public Home(int slot, String sector, Location location) {
        this.slot = slot;
        this.sector = sector;
        this.location = location;
    }
    public int getSlot() { return slot; }
    public String getSector() { return sector; }
    public Location getLocation() { return location; }
}