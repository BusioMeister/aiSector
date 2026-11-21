package ai.aisector.listeners;

import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.utils.BukkitTaskScheduler;
import net.kyori.adventure.text.Component;

import ai.aisector.utils.Messages;
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
        String channelPrefix = RedisChannels.GUI_DATA_RESPONSE_PREFIX;
        if (channel == null || !channel.startsWith(channelPrefix) || message == null
                || message.length() > 65_536) return;

        UUID playerUUID;
        try {
            String uuidText = channel.substring(channelPrefix.length());
            if (uuidText.length() != 36) return;
            playerUUID = UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Ignoring GUI response with invalid player UUID in channel " + channel);
            return;
        }

        final List<SectorGuiPayload> sectorsData;
        try {
            sectorsData = SectorGuiPayload.parse(message);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ignoring malformed sector GUI response: " + exception.getMessage());
            return;
        }

        // Używamy schedulera, bo jesteśmy w wątku Redis
        BukkitTaskScheduler.execute(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null || !player.isOnline()) return;
                try {
                    int visibleCount = Math.min(54, sectorsData.size());
                    int inventorySize = Math.max(9, (int) (Math.ceil(visibleCount / 9.0) * 9));
                    Inventory gui = Bukkit.createInventory(null, inventorySize,
                            Component.text(Messages.text("gui-data-listener.informacje-o-sektorach")));

                    for (int i = 0; i < visibleCount; i++) {
                        SectorGuiPayload data = sectorsData.get(i);
                        String name = data.name();
                        boolean isOnline = data.online();

                        ItemStack item = new ItemStack(isOnline ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
                        ItemMeta meta = item.getItemMeta();
                        meta.displayName(Component.text(name,
                                isOnline ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));

                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text(""));
                        if (isOnline) {
                            lore.add(Component.text(Messages.text("gui-data-listener.status")).color(NamedTextColor.GRAY)
                                    .append(Component.text(Messages.text("gui-data-listener.online"), NamedTextColor.GREEN)));
                            lore.add(Component.text(Messages.text("gui-data-listener.gracze")).color(NamedTextColor.GRAY)
                                    .append(Component.text(data.players(), NamedTextColor.WHITE)));
                            lore.add(Component.text(Messages.text("gui-data-listener.tps")).color(NamedTextColor.GRAY)
                                    .append(Component.text(data.tps(), NamedTextColor.WHITE)));
                            lore.add(Component.text(Messages.text("gui-data-listener.ram")).color(NamedTextColor.GRAY)
                                    .append(Component.text(Messages.text("gui-data-listener.mb", data.ramMb()), NamedTextColor.WHITE)));
                        } else {
                            lore.add(Component.text(Messages.text("gui-data-listener.status")).color(NamedTextColor.GRAY)
                                    .append(Component.text(Messages.text("gui-data-listener.offline"), NamedTextColor.RED)));
                        }

                        Sector sectorInfo = sectorManager.getSectorByName(name);
                        if (sectorInfo != null) {
                            lore.add(Component.text(""));
                            lore.add(Component.text(Messages.text("gui-data-listener.wielkosc")).color(NamedTextColor.GRAY)
                                    .append(Component.text(Messages.text("gui-data-listener.x", sectorInfo.getWidth(), sectorInfo.getDepth()),
                                            NamedTextColor.WHITE)));
                        }

                        meta.lore(lore);
                        item.setItemMeta(meta);
                        gui.setItem(i, item);
                    }
                    player.openInventory(gui);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Ignoring malformed sector GUI response: " + exception.getMessage());
                }
            });
    }
}
