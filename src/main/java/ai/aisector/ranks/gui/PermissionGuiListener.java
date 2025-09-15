package ai.aisector.ranks.gui;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import redis.clients.jedis.Jedis;

import java.util.Set;

public class PermissionGuiListener implements Listener {

    private final SectorPlugin plugin;
    private final RankManager rankManager;

    private final NamespacedKey PERM_KEY;
    private final NamespacedKey NAV_KEY;

    public PermissionGuiListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();

        this.PERM_KEY = new NamespacedKey(plugin, "perm");
        this.NAV_KEY = new NamespacedKey(plugin, "nav");
    }

    @EventHandler
    @SuppressWarnings("unchecked")
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("§8Permisje: §c")) return;

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Nazwa rangi: po „§c” do „ §8…”
        int posC = title.indexOf("§c");
        int pos8 = title.indexOf(" §8", posC + 2);
        if (posC == -1 || pos8 == -1) return;
        String rankName = title.substring(posC + 2, pos8);

        Rank rank = rankManager.getRank(rankName);
        if (rank == null) return;

        // Bieżąca strona: liczba między „(s. ” a „/”
        int currentPage = 0;
        int idx = title.indexOf("§8(s. ");
        if (idx >= 0) {
            int slash = title.indexOf("/", idx);
            if (slash > idx) {
                String num = title.substring(idx + "§8(s. ".length(), slash).trim();
                try {
                    currentPage = Math.max(0, Integer.parseInt(num) - 1);
                } catch (NumberFormatException ignored) {}
            }
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Nawigacja
        String nav = pdc.get(NAV_KEY, PersistentDataType.STRING);
        if (nav != null) {
            int raw = event.getRawSlot();
            if ("prev".equals(nav) && raw == 45) {
                new PermissionGui(plugin, player, rank, currentPage - 1).open();
            } else if ("next".equals(nav) && raw == 53) {
                new PermissionGui(plugin, player, rank, currentPage + 1).open();
            }
            return;
        }

        // Node permisji
        String permission = pdc.get(PERM_KEY, PersistentDataType.STRING);
        if (permission == null || permission.isEmpty()) return;

        // Toggle wg aktualnego cache’u
        Set<String> directPerms;
        try {
            directPerms = (Set<String>) rankManager.getDirectPermissions(rank);
        } catch (ClassCastException ex) {
            directPerms = new java.util.HashSet<>();
            for (Object o : rankManager.getDirectPermissions(rank)) {
                if (o != null) directPerms.add(String.valueOf(o));
            }
        }

        boolean had = directPerms.contains(permission);
        if (had) {
            rankManager.removePermissionFromRank(rank, permission);
        } else {
            rankManager.addPermissionToRank(rank, permission);
        }

        // Odśwież w następnym ticku — użyj effectively final
        final int pageToOpen = currentPage;
        Bukkit.getScheduler().runTask(plugin, () ->
                new PermissionGui(plugin, player, rank, pageToOpen).open()
        );

        // Zastosuj permisje lokalnie
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rank playerRank = rankManager.getPlayerRank(p.getUniqueId());
            if (playerRank != null && playerRank.getId() == rank.getId()) {
                plugin.getPermissionManager().applyPlayerPermissions(p);
            }
        }

        // Powiadom inne serwery
        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            JsonObject updateData = new JsonObject();
            updateData.addProperty("rankName", rank.getName());
            updateData.addProperty("sourceServer", plugin.getConfig().getString("this-sector-name"));
            jedis.publish("aisector:rank_permissions_update", updateData.toString());
        }
    }
}
