package ai.aisector.commands;

import ai.aisector.utils.Messages;

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
            sender.sendMessage(Messages.text("tp-command.ta-komenda-musi-byc-wykonana-przez-gracza"));
            return true;
        }
        if (!sender.hasPermission("aisector.command.tp")) {
            sender.sendMessage(Messages.text("tp-command.nie-masz-uprawnien-do-tej-komendy"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Messages.text("tp-command.uzycie-tp"));
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];
        if (targetName.isBlank() || targetName.length() > 16) {
            admin.sendMessage(Messages.text("tp-command.nieprawidlowa-nazwa-gracza"));
            return true;
        }

        // Przypadek 1: Gracz jest na tym samym serwerze (działa jak dotychczas)
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            admin.teleport(targetPlayer.getLocation());
            admin.sendMessage(Messages.text("tp-command.przeteleportowano-do-gracza", targetName));
            return true;
        }
        if (plugin.getSectorManager().isLockedSpawnBackend() && !admin.isOp()) {
            admin.sendMessage(Messages.text("tp-command.ze-spawnu-mozna-wyjsc-przyciskiem-na-gabce"));
            return true;
        }

        // Przypadek 2: Gracz jest na innym serwerze.
        // Velocity coordinates the remote lookup and waits for the source backend save before transfer.
        JsonObject request = new JsonObject();
        request.addProperty("adminUUID", admin.getUniqueId().toString());
        request.addProperty("targetName", targetName);

        sender.sendMessage(Messages.text("tp-command.przetwarzanie-prosby-o-teleportacje-do-gracza-na"));
        plugin.getRedisManager().publishAsync(RedisChannels.ADMIN_TP_REQUEST, request.toString())
                .whenComplete((subscribers, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (!admin.isOnline()) return;
                    if (error != null || subscribers == null || subscribers == 0) {
                        sender.sendMessage(Messages.text("tp-command.nie-udalo-sie-wyslac-zadania-teleportacji-do"));
                    }
                }));

        return true;
    }
}
