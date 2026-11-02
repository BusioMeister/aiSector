package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.UserManager;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TpposCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final UserManager userManager;
    private final Gson gson = new Gson();

    public TpposCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.userManager = plugin.getUserManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.tppos")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("§cUżycie: /tppos <x> <y> <z>");
            return true;
        }

        Player player = (Player) sender;
        TpposCoordinates coordinates;
        try {
            coordinates = TpposCoordinates.parse(args[0], args[1], args[2],
                    player.getWorld().getMinHeight(), player.getWorld().getMaxHeight());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cWspółrzędne są nieprawidłowe lub poza granicami świata.");
            return true;
        }

        double x = coordinates.x(), y = coordinates.y(), z = coordinates.z();
        Location targetLocation = new Location(player.getWorld(), x, y, z);
        String targetSectorName = sectorManager.getSectorForLocation(targetLocation.getBlockX(),
                targetLocation.getBlockZ());
        String currentSectorName = plugin.getConfig().getString("this-sector-name", "");

        if (targetSectorName == null || targetSectorName.isEmpty()) {
            player.sendMessage("§cPodane koordynaty nie należą do żadnego znanego sektora.");
            return true;
        }

        if (targetSectorName.equalsIgnoreCase(currentSectorName)) {
            java.util.UUID playerId = player.getUniqueId();
            player.teleportAsync(targetLocation).whenComplete((success, error) ->
                    ai.aisector.utils.BukkitTaskScheduler.execute(plugin, () -> {
                        if (!player.isOnline() || !player.getUniqueId().equals(playerId)
                                || Bukkit.getPlayer(playerId) != player) return;
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            player.sendMessage("§cNie udało się wykonać teleportu.");
                            return;
                        }
                        Sector target = sectorManager.getSectorForLocation(
                                targetLocation.getBlockX(), targetLocation.getBlockZ()) == null ? null
                                : sectorManager.getSectorByName(targetSectorName);
                        if (target != null) borderManager.sendWorldBorder(player, target);
                        player.sendMessage("§aPrzeteleportowano na koordynaty: §e" + (int) x + ", "
                                + (int) y + ", " + (int) z);
                    }));
        } else {
            player.sendMessage("§7Koordynaty znajdują się w innym sektorze. Rozpoczynam transfer...");

            try {
                String snapshot = userManager.serializePlayerDataForTransfer(player, player.getLocation());
                Map<String, String> transferKeys = Map.of(
                        "player:final_teleport_target:" + player.getUniqueId(), locationToJson(targetLocation));
                sectorManager.transferPlayerAsync(player, targetSectorName, snapshot, transferKeys);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not prepare coordinate transfer for " + player.getName()
                        + ": " + exception.getMessage());
                player.sendMessage("§cNie udało się przygotować transferu.");
                return true;
            }
        }
        return true;
    }

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
