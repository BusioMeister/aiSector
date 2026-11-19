package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ai.aisector.utils.BukkitTaskScheduler;

public class TpCommand implements CommandExecutor {

    private final SectorPlugin plugin;

    public TpCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.tp")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /tp <gracz>");
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];
        if (targetName.isBlank() || targetName.length() > 16) {
            admin.sendMessage("§cNieprawidłowa nazwa gracza.");
            return true;
        }

        // Przypadek 1: Gracz jest na tym samym serwerze (działa jak dotychczas)
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            admin.teleport(targetPlayer.getLocation());
            admin.sendMessage("§aPrzeteleportowano do gracza " + targetName + ".");
            return true;
        }
        if (plugin.getSectorManager().isLockedSpawnBackend() && !admin.isOp()) {
            admin.sendMessage("§cZe spawnu można wyjść przyciskiem na gąbce.");
            return true;
        }

        // Przypadek 2: Gracz jest na innym serwerze.
        // Velocity coordinates the remote lookup and waits for the source backend save before transfer.
        JsonObject request = new JsonObject();
        request.addProperty("adminUUID", admin.getUniqueId().toString());
        request.addProperty("targetName", targetName);

        sender.sendMessage("§7Przetwarzanie prośby o teleportację do gracza na innym sektorze...");
        plugin.getRedisManager().publishAsync(RedisChannels.ADMIN_TP_REQUEST, request.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (!admin.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage("§cNie udało się wysłać żądania teleportacji do proxy.");
                    }
                }));

        return true;
    }
}
