package ai.aisector.user;

import org.bson.Document;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Keeps the observed slot generation with the menu, so an old green button cannot delete a new home. */
public final class HomeSlotMenu implements InventoryHolder {
    private final UUID owner;
    private final Map<Integer, List<Document>> observed;
    private Inventory inventory;

    public HomeSlotMenu(UUID owner, List<Document> homes) {
        this.owner = owner;
        observed = homes.stream().filter(home -> home != null && home.get("home_slot") instanceof Integer slot
                        && slot >= 1 && slot <= 5 && home.containsKey("_id"))
                .map(home -> {
                    Document identity = new Document("_id", home.get("_id"))
                            .append("player_uuid", owner.toString()).append("home_slot", home.getInteger("home_slot"));
                    if (home.containsKey("_revision")) identity.put("_revision", home.get("_revision"));
                    return identity;
                }).collect(Collectors.groupingBy(home -> home.getInteger("home_slot")));
    }

    public boolean belongsTo(UUID player) { return owner.equals(player); }
    public List<Document> observedSlot(int slot) {
        return observed.getOrDefault(slot, List.of()).stream().map(Document::new).toList();
    }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
