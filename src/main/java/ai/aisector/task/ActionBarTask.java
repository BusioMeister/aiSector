package ai.aisector.task;

import ai.aisector.utils.Messages;
import ai.aisector.SectorPlugin;

import ai.aisector.sectors.player.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarTask extends BukkitRunnable {
    private final SectorPlugin plugin;

    public ActionBarTask(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isCombatTagged(player)) continue;
            if (VanishManager.isVanished(player)) {
                player.sendActionBar(Component.text(Messages.text("action-bar-task.vanish"), NamedTextColor.GREEN, TextDecoration.BOLD));
            }
        }
    }
}
