package ai.aisector.listeners;

import ai.aisector.database.MongoDBManager;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.InventorySerializer;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class BackupGuiListener implements Listener {

    private final MongoDBManager mongoDBManager;
    private final RedisManager redisManager;

    public BackupGuiListener(MongoDBManager mongoDBManager, RedisManager redisManager) {
        this.mongoDBManager = mongoDBManager;
        this.redisManager = redisManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player admin = (Player) event.getWhoClicked();

        if (title.startsWith("§8Backupy gracza: §c")) {
            handleMainGuiClick(event, admin);
        } else if (title.startsWith("§8Podgląd zgonu: ")) {
            handleViewGuiClick(event, admin);
        }
    }

    private void handleMainGuiClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;
        List<String> lore = clickedItem.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;
        String idLine = lore.get(lore.size() - 1);
        if (!idLine.startsWith("§0ID:")) return;
        ObjectId recordId = new ObjectId(idLine.substring(5));
        Document record = mongoDBManager.getCollection("death_backups").find(Filters.eq("_id", recordId)).first();
        if (record == null) {
            admin.sendMessage("§cWystąpił błąd: Nie znaleziono tego zapisu śmierci.");
            return;
        }
        String targetName = record.getString("victim_name");

        if (event.isRightClick()) {
            String invData = record.getString("inventory_data");
            try {
                Inventory viewGui = InventorySerializer.createGuiFromJson(invData, "§8Podgląd zgonu: " + targetName);

                // 🔥 ZMIANA: Dodajemy przycisk i wypełniacze do 45-slotowego GUI (jak w /invsee) 🔥
                ItemStack backButton = new ItemStack(Material.BARRIER);
                ItemMeta backMeta = backButton.getItemMeta();
                backMeta.setDisplayName("§cPowrót");
                backMeta.setLore(Collections.singletonList("§7Kliknij, aby wrócić do listy zgonów."));
                backButton.setItemMeta(backMeta);
                viewGui.setItem(44, backButton); // Ustawiamy przycisk w ostatnim slocie

                ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta fillerMeta = filler.getItemMeta();
                fillerMeta.setDisplayName(" ");
                filler.setItemMeta(fillerMeta);
                for (int i = 41; i < 44; i++) {
                    viewGui.setItem(i, filler);
                }

                admin.openInventory(viewGui);

            } catch (IOException e) {
                admin.sendMessage("§cWystąpił błąd podczas odczytu ekwipunku.");
                e.printStackTrace();
            }
        }

        if (event.isLeftClick()) {
            if (!record.getString("restored_by").equals("Nikt")) {
                admin.sendMessage("§cTen ekwipunek został już przywrócony przez §e" + record.getString("restored_by") + "§c.");
                return;
            }
            mongoDBManager.updateOne("death_backups",
                    Filters.eq("_id", recordId),
                    Updates.combine(
                            Updates.set("restored_by", admin.getName()),
                            Updates.set("restored_at", new Date())
                    )
            );
            String invData = record.getString("inventory_data");
            String victimUuid = record.getString("victim_uuid");
            Player victim = Bukkit.getPlayer(targetName);
            if (victim != null && victim.isOnline()) {
                InventorySerializer.deserializeAndUpdateInventory(victim.getInventory(), invData);
                victim.sendMessage("§aTwój ekwipunek ze śmierci został przywrócony przez administratora.");
            } else {
                try (Jedis jedis = redisManager.getJedis()) {
                    jedis.setex("player:pending_backup:" + victimUuid, 86400, invData);
                }
            }
            admin.sendMessage("§aPomyślnie przywrócono backup dla gracza §e" + targetName + "§a.");
            admin.closeInventory();
        }
    }

    private void handleViewGuiClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);

        // 🔥 ZMIANA: Sprawdzamy nowy slot przycisku "Powrót" 🔥
        if (event.getSlot() == 44 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
            String title = event.getView().getTitle();
            String targetName = title.substring(17);
            admin.performCommand("backup " + targetName);
        }
    }
}