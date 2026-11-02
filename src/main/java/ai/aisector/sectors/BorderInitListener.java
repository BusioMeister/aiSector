package ai.aisector.sectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class BorderInitListener extends JedisPubSub {

    private final SectorManager sectorManager;
    private final JavaPlugin plugin;
    // 🔥 ZMIANA: Dodajemy pole dla WorldBorderManager 🔥
    private final WorldBorderManager borderManager;

    // 🔥 ZMIANA: Aktualizujemy konstruktor, aby przyjmował nowy manager 🔥
    public BorderInitListener(SectorManager sectorManager, JavaPlugin plugin, WorldBorderManager borderManager) {
        this.sectorManager = sectorManager;
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    @Override
    public void onMessage(String channel, String message) {
        if (channel == null || !channel.startsWith("sector-border-init:")
                || message == null || message.length() != 36) return;

        String sectorName = channel.substring("sector-border-init:".length());
        if (!sectorName.matches("[A-Za-z0-9_-]{1,64}")) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(message);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy UUID w wiadomości Redis: " + message);
            return;
        }

        BukkitTaskScheduler.execute(plugin, () -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // To nie jest błąd, gracz mógł się wylogować w międzyczasie
                return;
            }

            Sector sector = sectorManager.getSectorByName(sectorName);
            if (sector == null) {
                plugin.getLogger().warning("Nie znaleziono sektora do inicjalizacji bordera: " + sectorName);
                return;
            }

            // 🔥 POPRAWKA: Używamy teraz poprawnej metody z WorldBorderManager 🔥
            borderManager.sendWorldBorder(player, sector);
            plugin.getLogger().info("✅ Zainicjowano border dla gracza " + player.getName() + " w sektorze: " + sectorName);
        }, 5L));
    }
}
