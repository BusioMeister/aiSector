package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.user.UserManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class TpCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final UserManager userManager;

    public TpCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
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

        // Przypadek 1: Gracz jest na tym samym serwerze (działa jak dotychczas)
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            admin.teleport(targetPlayer.getLocation());
            admin.sendMessage("§aPrzeteleportowano do gracza " + targetName + ".");
            return true;
        }

        // Przypadek 2: Gracz jest na innym serwerze.
        // Krok A: Zapisujemy dane admina, aby były gotowe do transferu.
        userManager.savePlayerDataForTransfer(admin, admin.getLocation());

        // Krok B: Wysyłamy prośbę do Velocity o zorganizowanie teleportacji.
        JsonObject request = new JsonObject();
        request.addProperty("adminUUID", admin.getUniqueId().toString());
        request.addProperty("targetName", targetName);

        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            jedis.publish("aisector:admin_tp_request", request.toString());
        }
        sender.sendMessage("§7Przetwarzanie prośby o teleportację do gracza na innym sektorze...");

        return true;
    }
}