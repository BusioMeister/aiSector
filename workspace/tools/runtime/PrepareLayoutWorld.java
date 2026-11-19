import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;

/** Generation fixture for fresh, backed-up copies only. Not part of either plugin artifact. */
public final class PrepareLayoutWorld extends JavaPlugin {
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || !Bukkit.getOnlinePlayers().isEmpty()
                || getServer().getPort() != 25566 || !getDataFolder().toPath().toAbsolutePath().normalize().toString()
                .contains("aisector-map-package-")) return true;
        World world = Bukkit.getWorlds().getFirst();
        var queue = new ArrayDeque<int[]>();
        // Full map (-500..499) plus a one-chunk outer margin.
        for (int x = -33; x <= 32; x++) {
            for (int z = -33; z <= 32; z++) queue.add(new int[] {x, z});
        }
        generate(world, queue);
        return true;
    }

    private void generate(World world, ArrayDeque<int[]> remaining) {
        if (remaining.isEmpty()) {
            try { Files.writeString(Path.of("chunks-ready.txt"), "4356 chunks generated\n"); }
            catch (Exception error) { getLogger().severe("Could not record world generation: " + error.getMessage()); }
            return;
        }
        var batch = new java.util.ArrayList<CompletableFuture<?>>();
        for (int i = 0; i < 24 && !remaining.isEmpty(); i++) {
            int[] pos = remaining.removeFirst();
            batch.add(world.getChunkAtAsync(pos[0], pos[1]));
        }
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        getLogger().severe("Chunk generation failed: " + error.getMessage());
                        return;
                    }
                    generate(world, remaining);
                }));
    }
}
