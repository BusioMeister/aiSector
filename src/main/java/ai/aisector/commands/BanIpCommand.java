package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import ai.aisector.shared.NetworkAddressUtil;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class BanIpCommand implements CommandExecutor {
    private final SectorPlugin plugin;

    public BanIpCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.ban.ip")) {
            sender.sendMessage(Messages.text("ban-ip-command.brak-uprawnien"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.text("ban-ip-command.uzycie-banip"));
            return true;
        }

        String target = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (target.isBlank() || target.length() > 128 || reason.length() > 256) {
            sender.sendMessage(Messages.text("ban-ip-command.nieprawidlowy-cel-lub-powod-maksymalnie-256-znakow"));
            return true;
        }
        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Messages.text("ban-ip-command.podaj-liczbe-minut-lub-1"));
            return true;
        }

        boolean permanent = minutes == -1;
        if (minutes == 0 || minutes < -1) {
            sender.sendMessage(Messages.text("ban-ip-command.czas-bana-musi-wynosic-co-najmniej-minute"));
            return true;
        }
        if (permanent && !sender.hasPermission("aisector.ban.perm")) {
            sender.sendMessage(Messages.text("ban-ip-command.perm-ban-wymaga-aisector-ban-perm"));
            return true;
        }
        long maxMinutes = 3L * 24 * 60;
        if (!permanent && minutes > maxMinutes && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage(Messages.text("ban-ip-command.maksymalnie-3-dni-bez-aisector-ban-long"));
            return true;
        }
        if (!permanent && minutes > (Long.MAX_VALUE - System.currentTimeMillis()) / 60_000L) {
            sender.sendMessage(Messages.text("ban-ip-command.podany-czas-bana-jest-zbyt-dlugi"));
            return true;
        }

        String bannedBy = sender instanceof Player player ? player.getName() : "Konsola";
        Player onlinePlayer = Bukkit.getPlayerExact(target);

        if (NetworkAddressUtil.looksLikeIpLiteral(target)) {
            Optional<String> normalizedIp = NetworkAddressUtil.normalizeIpLiteral(target);
            if (normalizedIp.isEmpty()) {
                sender.sendMessage(Messages.text("ban-ip-command.podaj-prawidlowy-adres-ip"));
                return true;
            }
            persistBan(sender, normalizedIp.get(), "(IP ban)", null, reason, bannedBy, minutes, permanent);
            return true;
        }
        if (onlinePlayer != null && onlinePlayer.getAddress() != null
                && onlinePlayer.getAddress().getAddress() != null) {
            String ip = onlinePlayer.getAddress().getAddress().getHostAddress();
            Optional<String> normalizedIp = NetworkAddressUtil.normalizeIpLiteral(ip);
            if (normalizedIp.isPresent()) {
                persistBan(sender, normalizedIp.get(), onlinePlayer.getName(), onlinePlayer.getUniqueId(),
                        reason, bannedBy, minutes, permanent);
                return true;
            }
        }

        sender.sendMessage(Messages.text("ban-ip-command.wyszukuje-ostatnie-znane-ip-gracza"));
        NameResolver resolver = new NameResolver(plugin);
        resolver.resolveUuidAsync(target)
                .thenCompose(uuid -> uuid.map(resolver::resolveLastIpAsync)
                        .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()))
                        .thenApply(ip -> new ResolvedTarget(uuid.orElse(null), ip.orElse(null))))
                .whenComplete((resolved, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null || resolved == null || resolved.ip() == null) {
                        sender.sendMessage(Messages.text("ban-ip-command.nie-mozna-ustalic-ip-gracz-offline-i"));
                        return;
                    }
                    persistBan(sender, resolved.ip(), target, resolved.uuid(), reason, bannedBy, minutes, permanent);
                }));
        return true;
    }

    private record ResolvedTarget(UUID uuid, String ip) { }

    private void persistBan(CommandSender sender, String ip, String displayName, UUID kickUuid,
                            String reason, String bannedBy, long minutes, boolean permanent) {
        long now = System.currentTimeMillis();
        long expiresAt = permanent ? -1 : now + minutes * 60_000L;

        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", "ip");
        broadcast.addProperty("name", displayName);
        broadcast.addProperty("by", bannedBy);
        broadcast.addProperty("reason", reason);
        broadcast.addProperty("perm", permanent);
        broadcast.addProperty("minutes", minutes);

        JsonObject kick = new JsonObject();
        if (kickUuid != null) {
            kick.addProperty("uuid", kickUuid.toString());
            kick.addProperty("message", banMessage(displayName, bannedBy, reason, expiresAt));
        }
        String banId = UUID.randomUUID().toString();

        plugin.getRedisManager().executeAsync(jedis -> BanRedisScripts.writeBan(jedis,
                "ban:ip:" + ip, "bans:ips", ip, "ip", displayName, reason, bannedBy,
                now, expiresAt, banId, broadcast.toString(), kickUuid == null ? null : kick.toString()))
                .whenComplete((saved, error) -> BukkitTaskScheduler.execute(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) return;
            if (error != null) {
                plugin.getLogger().warning("Could not persist IP ban for " + displayName + ": " + error.getMessage());
                sender.sendMessage(Messages.text("ban-ip-command.nie-udalo-sie-zapisac-bana-ip-w"));
                return;
            }
            if (kickUuid != null) {
                Player online = Bukkit.getPlayer(kickUuid);
                if (online != null) {
                    online.kick(net.kyori.adventure.text.Component.text(
                            banMessage(displayName, bannedBy, reason, expiresAt)));
                }
            }
            sender.sendMessage(Messages.text("ban-ip-command.zbanowano-ip-na", displayName, permanent
                    ? Messages.text("moderation.permanent") : Messages.text("moderation.minutes", minutes)));
        }));
    }

    private String banMessage(String name, String by, String reason, long expiresAt) {
        String time = expiresAt == -1 ? Messages.text("moderation.permanent")
                : Messages.text("moderation.until", java.time.Instant.ofEpochMilli(expiresAt));
        return Messages.text("moderation.ban-ip-screen", name, reason, by, time);
    }
}
