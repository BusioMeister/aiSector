package ai.aisector.sectors;

import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class BorderInitListener extends JedisPubSub {

    private final SectorManager sectorManager;
    private final JavaPlugin plugin;

    public BorderInitListener(SectorManager sectorManager, JavaPlugin plugin) {
        this.sectorManager = sectorManager;
        this.plugin = plugin;
    }

    @Override
    public void onMessage(String channel, String message) {
        // Oczekiwany format: channel = sector-border-init:SectorName, message = UUID gracza
        if (!channel.startsWith("sector-border-init:")) return;

        String sectorName = channel.substring("sector-border-init:".length());

        UUID uuid;
        try {
            uuid = UUID.fromString(message);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy UUID w wiadomości Redis: " + message);
            return;
        }

        // Wykonaj na głównym wątku
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                plugin.getLogger().warning("Nie znaleziono gracza o UUID: " + uuid);
                return;
            }

            Sector sector = sectorManager.getSectorByName(sectorName);
            if (sector == null) {
                plugin.getLogger().warning("Nie znaleziono sektora: " + sectorName);
                return;
            }

            sectorManager.applyBorder(player, sector);
            plugin.getLogger().info("✅ Ustawiono border dla gracza " + player.getName() + " w sektorze: " + sectorName);
        },5L);
    }
}
