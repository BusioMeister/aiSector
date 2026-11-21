package ai.aisector.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Inventory identity must never depend on its editable title. */
public final class PluginMenu implements InventoryHolder {
    public enum Kind { BACKUPS, BACKUP_VIEW, INVSEE, HOME, DROP, STONE, COBBLEX }
    private final Kind kind;
    private final String target;
    private final Inventory inventory;

    public PluginMenu(Kind kind, String target, int size, String title) {
        this.kind = kind;
        this.target = target;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public static boolean is(Inventory inventory, Kind kind) {
        return inventory.getHolder() instanceof PluginMenu menu && menu.kind == kind;
    }

    public String target() { return target; }
    @Override public Inventory getInventory() { return inventory; }
}
