package ai.aisector.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Small shutdown-safe handoff for completions produced by I/O threads. */
public final class BukkitTaskScheduler {
    private BukkitTaskScheduler() { }

    public static boolean execute(JavaPlugin plugin, Runnable task) {
        if (plugin == null || task == null || !plugin.isEnabled()) return false;
        try {
            Bukkit.getScheduler().runTask(plugin, task);
            return true;
        } catch (RuntimeException shuttingDown) {
            return false;
        }
    }
}
