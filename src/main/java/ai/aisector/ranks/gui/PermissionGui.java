package ai.aisector.ranks.gui;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.PermissionManager;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PermissionGui {

    private final SectorPlugin plugin;
    private final Player viewer;
    private final Rank targetRank;
    private final int page;
    private final PermissionManager permissionManager;
    private final RankManager rankManager;

    private static final int PERMISSIONS_PER_PAGE = 45;

    private final NamespacedKey PERM_KEY;
    private final NamespacedKey NAV_KEY;

    public PermissionGui(SectorPlugin plugin, Player viewer, Rank targetRank, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetRank = targetRank;
        this.page = Math.max(0, page);
        this.permissionManager = plugin.getPermissionManager();
        this.rankManager = plugin.getRankManager();

        this.PERM_KEY = new NamespacedKey(plugin, "perm");
        this.NAV_KEY = new NamespacedKey(plugin, "nav");
    }

    @SuppressWarnings("unchecked")
    public void open() {
        List<String> allPerms;
        try {
            allPerms = (List<String>) permissionManager.getAvailablePermissions();
        } catch (ClassCastException ex) {
            allPerms = new ArrayList<>();
            for (Object o : permissionManager.getAvailablePermissions()) {
                if (o != null) allPerms.add(String.valueOf(o));
            }
        }

        int maxPages = Math.max(1, (int) Math.ceil(allPerms.size() / (double) PERMISSIONS_PER_PAGE));
        int currentPage = Math.min(this.page, maxPages - 1);

        Inventory gui = Bukkit.createInventory(null, 54,
                "§8Permisje: §c" + targetRank.getName() + " §8(s. " + (currentPage + 1) + "/" + maxPages + ")");

        Set<String> rankPerms;
        try {
            rankPerms = (Set<String>) rankManager.getDirectPermissions(targetRank);
        } catch (ClassCastException ex) {
            rankPerms = new java.util.HashSet<>();
            for (Object o : rankManager.getDirectPermissions(targetRank)) {
                if (o != null) rankPerms.add(String.valueOf(o));
            }
        }

        int startIndex = currentPage * PERMISSIONS_PER_PAGE;

        for (int i = 0; i < PERMISSIONS_PER_PAGE; i++) {
            int permIndex = startIndex + i;
            if (permIndex >= allPerms.size()) break;

            String permission = allPerms.get(permIndex);
            boolean hasPerm = rankPerms.contains(permission);

            ItemStack item = new ItemStack(hasPerm ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + permission);
            meta.setLore(Collections.singletonList(hasPerm ? "§aKliknij, aby ODEBRAĆ" : "§cKliknij, aby NADAĆ"));
            meta.getPersistentDataContainer().set(PERM_KEY, PersistentDataType.STRING, permission);
            item.setItemMeta(meta);

            gui.setItem(i, item);
        }

        if (currentPage > 0) {
            gui.setItem(45, createNavItem("§cPoprzednia strona", Material.ARROW, "prev"));
        }
        if (currentPage < maxPages - 1) {
            gui.setItem(53, createNavItem("§aNastępna strona", Material.ARROW, "next"));
        }

        viewer.openInventory(gui);
    }

    private ItemStack createNavItem(String name, Material material, String navValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.STRING, navValue);
        item.setItemMeta(meta);
        return item;
    }
}
