package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.util.Optional;
import java.util.UUID;

public class KickCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public KickCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.kick")) { sender.sendMessage("§cBrak uprawnień."); return true; }
        if (args.length < 2) { sender.sendMessage("§cUżycie: /kick <nick> <powód>"); return true; }

        String targetNameArg = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String by = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";

        NameResolver resolver = new NameResolver(plugin);
        Optional<UUID> optUuid = resolver.resolveUuid(targetNameArg);
        if (optUuid.isEmpty()) { sender.sendMessage("§cNie znaleziono gracza: " + targetNameArg); return true; }
        UUID uuid = optUuid.get();

        String kickMsg = "§cZostałeś wyrzucony!\n§7Powód: §f" + reason + "\n§7Przez: §f" + by;

        try (Jedis j = plugin.getRedisManager().getJedis()) {
            // Broadcast globalny (type=kick)
            JSONObject bc = new JSONObject();
            bc.put("type", "kick");
            bc.put("name", targetNameArg);
            bc.put("by", by);
            bc.put("reason", reason);
            j.publish("aisector:ban_broadcast", bc.toString());

            // Natychmiastowy kick na proxy
            JSONObject kick = new JSONObject();
            kick.put("uuid", uuid.toString());
            kick.put("message", kickMsg);
            j.publish("aisector:ban_kick", kick.toString());
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) online.kick(net.kyori.adventure.text.Component.text(kickMsg));

        sender.sendMessage("§aWyrzucono " + targetNameArg + " §8- §f" + reason);
        return true;
    }
}
