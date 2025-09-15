package ai.aisector.listeners;

import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiDataListener extends JedisPubSub {

    private final JavaPlugin plugin;
    private final SectorManager sectorManager;

    public GuiDataListener(JavaPlugin plugin, SectorManager sectorManager) {
        this.plugin = plugin;
        this.sectorManager = sectorManager;
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        if (!channel.startsWith("aisector:gui_data_response:")) return;

        UUID playerUUID = UUID.fromString(channel.substring("aisector:gui_data_response:".length()));
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) return;

        // Używamy schedulera, bo jesteśmy w wątku Redis
        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonArray sectorsData = JsonParser.parseString(message).getAsJsonArray();
            int invSize = (int) (Math.ceil(sectorsData.size() / 9.0) * 9);
            invSize = Math.max(9, invSize); // Minimalny rozmiar to 9
            Inventory gui = Bukkit.createInventory(null, invSize, Component.text("Informacje o Sektorach"));

            for (int i = 0; i < sectorsData.size(); i++) {
                JsonObject data = sectorsData.get(i).getAsJsonObject();
                String name = data.get("name").getAsString();
                boolean isOnline = data.get("isOnline").getAsBoolean();

                ItemStack item = new ItemStack(isOnline ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(name, isOnline ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                if (isOnline) {
                    lore.add(Component.text("Status: ").color(NamedTextColor.GRAY).append(Component.text("Online", NamedTextColor.GREEN)));
                    lore.add(Component.text("Gracze: ").color(NamedTextColor.GRAY).append(Component.text(data.get("players").getAsInt(), NamedTextColor.WHITE)));
                    lore.add(Component.text("TPS: ").color(NamedTextColor.GRAY).append(Component.text(data.get("tps").getAsString(), NamedTextColor.WHITE)));
                    lore.add(Component.text("RAM: ").color(NamedTextColor.GRAY).append(Component.text(data.get("ram").getAsInt() + " MB", NamedTextColor.WHITE)));
                } else {
                    lore.add(Component.text("Status: ").color(NamedTextColor.GRAY).append(Component.text("Offline", NamedTextColor.RED)));
                }

                // Dodaj informacje o wielkości z sectors.yml
                Sector sectorInfo = sectorManager.getSectorByName(name);
                if (sectorInfo != null) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("Wielkość: ").color(NamedTextColor.GRAY).append(Component.text(sectorInfo.getWidth() + "x" + sectorInfo.getDepth(), NamedTextColor.WHITE)));
                }

                meta.lore(lore);
                item.setItemMeta(meta);
                gui.setItem(i, item);
            }
            player.openInventory(gui);
        });
    }
}