package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class BanCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    public BanCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.ban.use")) { sender.sendMessage(Messages.text("ban-command.brak-uprawnien")); return true; }
        if (args.length < 3) { sender.sendMessage(Messages.text("ban-command.uzycie-ban")); return true; }

        String targetNameArg = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (targetNameArg.isBlank() || targetNameArg.length() > 16 || reason.length() > 256) {
            sender.sendMessage(Messages.text("ban-command.nieprawidlowy-nick-lub-powod-maksymalnie-256-znakow"));
            return true;
        }
        long minutes;
        try { minutes = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage(Messages.text("ban-command.podaj-liczbe-minut-lub-1")); return true; }

        boolean isPerm = minutes == -1;
        if (minutes == 0 || minutes < -1) {
            sender.sendMessage(Messages.text("ban-command.czas-bana-musi-wynosic-co-najmniej-minute"));
            return true;
        }
        if (isPerm && !sender.hasPermission("aisector.ban.perm")) { sender.sendMessage(Messages.text("ban-command.perm-ban-wymaga-aisector-ban-perm")); return true; }
        long max = 3L * 24 * 60; // 3 dni
        if (!isPerm && minutes > max && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage(Messages.text("ban-command.maksymalnie-3-dni-bez-aisector-ban-long")); return true;
        }
        if (!isPerm && minutes > (Long.MAX_VALUE - System.currentTimeMillis()) / 60_000L) {
            sender.sendMessage(Messages.text("ban-command.podany-czas-bana-jest-zbyt-dlugi"));
            return true;
        }
        String bannedBy = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";

        sender.sendMessage(Messages.text("ban-command.wyszukuje-gracza-i-zapisuje-bana"));
        new NameResolver(plugin).resolveUuidAsync(targetNameArg).whenComplete((resolvedUuid, resolveError) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (resolveError != null || resolvedUuid == null || resolvedUuid.isEmpty()) {
                        sender.sendMessage(resolveError == null
                                ? Messages.text("ban-command.nie-znaleziono-gracza", targetNameArg)
                                : Messages.text("ban-command.nie-udalo-sie-wyszukac-gracza"));
                        return;
                    }
                    persistBan(sender, targetNameArg, resolvedUuid.get(), reason, bannedBy, minutes, isPerm);
                }));
        return true;
    }

    private void persistBan(CommandSender sender, String targetName, UUID uuid, String reason,
                            String bannedBy, long minutes, boolean permanent) {
        long expiresAt = permanent ? -1 : System.currentTimeMillis() + minutes * 60_000L;
        JSONObject broadcast = new JSONObject();
        broadcast.put("type", "user");
        broadcast.put("name", targetName);
        broadcast.put("by", bannedBy);
        broadcast.put("reason", reason);
        broadcast.put("perm", permanent);
        broadcast.put("minutes", minutes);

        JSONObject kick = new JSONObject();
        kick.put("uuid", uuid.toString());
        kick.put("message", banMessage(targetName, bannedBy, reason, expiresAt));
        String banId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();

        plugin.getRedisManager().executeAsync(jedis -> BanRedisScripts.writeBan(jedis,
                "ban:user:" + uuid, "bans:users", uuid.toString(), "user", targetName, reason, bannedBy,
                createdAt, expiresAt, banId, broadcast.toString(), kick.toString()))
                .whenComplete((saved, error) -> BukkitTaskScheduler.execute(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) return;
            if (error != null) {
                plugin.getLogger().warning("Could not persist ban for " + uuid + ": " + error.getMessage());
                sender.sendMessage(Messages.text("ban-command.nie-udalo-sie-zapisac-bana-w-redis"));
                return;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.kick(net.kyori.adventure.text.Component.text(banMessage(targetName, bannedBy, reason, expiresAt)));
            }
            sender.sendMessage(Messages.text("ban-command.zbanowano-na", targetName, permanent
                    ? Messages.text("moderation.permanent") : Messages.text("moderation.minutes", minutes)));
        }));
    }

    private String banMessage(String name, String by, String reason, long expiresAt) {
        String time = expiresAt == -1 ? Messages.text("moderation.permanent")
                : Messages.text("moderation.until", Instant.ofEpochMilli(expiresAt));
        return Messages.text("moderation.ban-screen", name, reason, by, time);
    }
}
