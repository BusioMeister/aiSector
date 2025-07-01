package ai.aisector;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class BorderBossBarHandler {

    private final JavaPlugin plugin;
    private final Map<Player, BossBar> playerBars = new HashMap<>();

    public BorderBossBarHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateBossBar(Player player, int distance, boolean show) {
        BossBar bar = playerBars.computeIfAbsent(player, p -> {
            BossBar newBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            newBar.addPlayer(p);
            return newBar;
        });

        if (!show) {
            bar.setVisible(false);
            return;
        }

        bar.setTitle("§cZbliżasz się do granicy sektora: §e" + distance + " kratek");
        double progress = Math.max(0.0, Math.min(1.0, distance / 50.0)); // 0..1
        bar.setProgress(progress);
        bar.setVisible(true);
    }

    public void removeBossBar(Player player) {
        BossBar bar = playerBars.remove(player);
        if (bar != null) {
            bar.removeAll();
        }
    }
}
