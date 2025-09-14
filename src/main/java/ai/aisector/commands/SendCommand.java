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

public class SendCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final UserManager userManager;

    public SendCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.send")) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUżycie: /send <gracz> <sektor>");
            return true;
        }

        String targetName = args[0];
        String targetSector = args[1];

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage("§cGracz o nicku '" + targetName + "' nie jest online na tym serwerze. Wysyłam prośbę do sieci...");
        } else {
            // Krok A: Zapisujemy dane gracza, aby były gotowe do transferu.
            userManager.savePlayerDataForTransfer(targetPlayer, targetPlayer.getLocation());
        }

        // Krok B: Wysyłamy prośbę do Velocity o zorganizowanie transferu.
        JsonObject request = new JsonObject();
        request.addProperty("requesterName", sender.getName());
        request.addProperty("targetName", targetName);
        request.addProperty("targetSector", targetSector);

        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            jedis.publish("aisector:send_request", request.toString());
        }
        sender.sendMessage("§7Przetwarzanie prośby o wysłanie gracza...");
        return true;
    }
}