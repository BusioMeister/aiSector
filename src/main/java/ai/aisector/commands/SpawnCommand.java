package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.SectorManager;
import ai.aisector.listeners.GlobalSpawnData;
import ai.aisector.user.UserManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public class SpawnCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final UserManager userManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public SpawnCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.userManager = plugin.getUserManager();
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
        String currentSectorName = plugin.getConfig().getString("this-sector-name", "");

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

        if (targetSector.equalsIgnoreCase(currentSectorName)) {
            player.sendMessage(Messages.text("spawn-command.teleportuje-na-spawn"));
            try {
                player.teleportAsync(spawnLocation).whenComplete((success, error) -> {
                    BukkitTaskScheduler.execute(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null || !Boolean.TRUE.equals(success)) {
                                plugin.getLogger().warning("Could not teleport " + player.getName()
                                        + " to the local spawn" + (error == null ? "" : ": " + error.getMessage()));
                                player.sendMessage(Messages.text("spawn-command.nie-udalo-sie-teleportowac-na-spawn"));
                                return;
                            }
                            player.sendMessage(Messages.text("spawn-command.zostales-przeteleportowany-na-spawn"));
                    });
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not start local spawn teleport for " + player.getName()
                        + ": " + exception.getMessage());
                player.sendMessage(Messages.text("spawn-command.nie-udalo-sie-teleportowac-na-spawn"));
            }
        } else {
            try {
                String snapshot = userManager.serializePlayerDataForTransfer(player, player.getLocation());
                Map<String, String> transferKeys = Map.of(
                        "player:final_teleport_target:" + player.getUniqueId(), locationToJson(spawnLocation));
                sectorManager.transferPlayerAsync(player, targetSector, snapshot, transferKeys);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not prepare spawn transfer for " + player.getName()
                        + ": " + exception.getMessage());
                player.sendMessage(Messages.text("spawn-command.nie-udalo-sie-przygotowac-transferu-na-spawn"));
                return;
            }
            player.sendMessage(Messages.text("spawn-command.trwa-bezpieczny-transfer-na-serwer-spawnu"));
        }
    }

    // Metoda pomocnicza do konwersji Location na JSON
    private String locationToJson(Location loc) {
        Map<String, Object> data = new HashMap<>();
        data.put("world", loc.getWorld().getName());
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        return gson.toJson(data);
    }
}
