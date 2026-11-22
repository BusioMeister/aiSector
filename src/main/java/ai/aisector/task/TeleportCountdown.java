package ai.aisector.task;

import ai.aisector.utils.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class TeleportCountdown {
    private final long duration;
    private long deadline;

    TeleportCountdown(int seconds) {
        duration = TimeUnit.SECONDS.toNanos(seconds);
    }

    void start() {
        deadline = System.nanoTime() + duration;
    }

    boolean show(Player player, BossBar bar) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            clear(player);
            return false;
        }
        long millis = (remaining + 999_999) / 1_000_000;
        String time = String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
        bar.setTitle(Messages.text("teleport.timer-title", time));
        bar.setProgress(Math.clamp((double) remaining / duration, 0.0, 1.0));
        player.sendActionBar(Component.text(Messages.text("teleport.timer-actionbar", time), NamedTextColor.YELLOW));
        return true;
    }

    void clear(Player player) {
        if (deadline != 0 && player.isOnline()) player.sendActionBar(Component.empty());
    }
}
