package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.listeners.GlobalSpawnData;
import ai.aisector.task.BaseTeleportWarmupTask;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RedisManager redisManager;

    public SpawnCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("spawn-command.ta-komenda-moze-byc-wykonana-tylko-przez"));
            return true;
        }

        Player player = (Player) sender;
        player.sendMessage(Messages.text("spawn-command.pobieram-lokalizacje-globalnego-spawnu"));
        redisManager.getAsync(RedisChannels.GLOBAL_SPAWN).whenComplete((spawnDataJson, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().warning("Could not read global spawn: " + error.getMessage());
                        player.sendMessage(Messages.text("spawn-command.nie-udalo-sie-odczytac-globalnego-spawnu"));
                        return;
                    }
                    teleportToGlobalSpawn(player, spawnDataJson);
                }));
        return true;
    }

    private void teleportToGlobalSpawn(Player player, String spawnDataJson) {
        if (spawnDataJson == null || spawnDataJson.isEmpty()) {
            player.sendMessage(Messages.text("spawn-command.spawn-serwera-nie-zostal-jeszcze-ustawiony"));
            return;
        }

        GlobalSpawnData spawnData;
        try {
            spawnData = GlobalSpawnData.parse(spawnDataJson);
        } catch (RuntimeException exception) {
            player.sendMessage(Messages.text("spawn-command.zapisany-spawn-jest-nieprawidlowy"));
            return;
        }
        String targetSector = spawnData.sector();

        Location spawnLocation;
        try {
            World world = Bukkit.getWorlds().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No world is loaded"));
            spawnLocation = new Location(world, spawnData.x(), spawnData.y(), spawnData.z(),
                    spawnData.yaw(), spawnData.pitch());
        } catch (RuntimeException exception) {
            player.sendMessage(Messages.text("spawn-command.lokalizacja-globalnego-spawnu-jest-nieprawidlowa"));
            return;
        }

        new BaseTeleportWarmupTask(player, spawnLocation, spawnLocation.getWorld().getName(), targetSector, 5,
                Messages.text("spawn-command.zostales-przeteleportowany-na-spawn"),
                "player:final_teleport_target:" + player.getUniqueId(), 60, plugin)
                .start(player.hasPermission("aisector.command.spawn.instant"));
    }
}
