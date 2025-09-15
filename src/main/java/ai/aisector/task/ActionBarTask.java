package ai.aisector.task;

import ai.aisector.sectors.player.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (VanishManager.isVanished(player)) {
                player.sendActionBar(Component.text("VANISH", NamedTextColor.GREEN, TextDecoration.BOLD));
            } else {
                // Wysyłamy pustą wiadomość, aby wyczyścić ActionBar.
                // Jest to bezpieczne i w pełni kompatybilne ze Spigotem i Paper.
                player.sendActionBar(Component.text(""));
            }
        }
    }
}