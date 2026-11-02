package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.resolver.NameResolver;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ai.aisector.utils.BukkitTaskScheduler;

import java.util.Arrays;
import java.util.UUID;

public final class KickCommand implements CommandExecutor {
    private final SectorPlugin plugin;

    public KickCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.kick")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /kick <nick> <powód>");
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (targetName.isBlank() || targetName.length() > 16 || reason.length() > 256) {
            sender.sendMessage("§cNieprawidłowy nick lub powód (maksymalnie 256 znaków).");
            return true;
        }
        String kickedBy = sender instanceof Player player ? player.getName() : "Konsola";
        String kickMessage = "§cZostałeś wyrzucony!\n§7Powód: §f" + reason + "\n§7Przez: §f" + kickedBy;
        sender.sendMessage("§7Wyszukuję gracza i wysyłam kick do proxy...");

        new NameResolver(plugin).resolveUuidAsync(targetName).whenComplete((uuid, lookupError) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (lookupError != null || uuid == null || uuid.isEmpty()) {
                        sender.sendMessage("§cNie znaleziono gracza: " + targetName);
                        return;
                    }
                    persistKick(sender, targetName, kickedBy, reason, uuid.get(), kickMessage);
                }));
        return true;
    }

    private void persistKick(CommandSender sender, String targetName, String kickedBy,
                             String reason, UUID uuid, String kickMessage) {
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", "kick");
        broadcast.addProperty("name", targetName);
        broadcast.addProperty("by", kickedBy);
        broadcast.addProperty("reason", reason);

        JsonObject kick = new JsonObject();
        kick.addProperty("uuid", uuid.toString());
        kick.addProperty("message", kickMessage);

        plugin.getRedisManager().executeAsync(jedis -> jedis.eval(
                "redis.call('publish', KEYS[1], ARGV[1]); return redis.call('publish', KEYS[2], ARGV[2])",
                java.util.List.of(RedisChannels.BAN_BROADCAST, RedisChannels.BAN_KICK),
                java.util.List.of(broadcast.toString(), kick.toString())))
                .whenComplete((published, error) -> BukkitTaskScheduler.execute(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) return;
            if (error != null) {
                plugin.getLogger().warning("Could not publish kick for " + uuid + ": " + error.getMessage());
                sender.sendMessage("§cNie udało się wysłać kicka przez proxy.");
                return;
            }
            Player localPlayer = Bukkit.getPlayer(uuid);
            if (localPlayer != null) {
                localPlayer.kick(net.kyori.adventure.text.Component.text(kickMessage));
            }
            sender.sendMessage("§aWyrzucono " + targetName + " §8- §f" + reason);
        }));
    }
}
