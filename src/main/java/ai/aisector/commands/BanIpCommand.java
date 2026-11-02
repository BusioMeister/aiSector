package ai.aisector.commands;

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
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /banip <nick|ip> <minuty|-1> <powód>");
            return true;
        }

        String target = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (target.isBlank() || target.length() > 128 || reason.length() > 256) {
            sender.sendMessage("§cNieprawidłowy cel lub powód (maksymalnie 256 znaków).");
            return true;
        }
        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cPodaj liczbę minut lub -1.");
            return true;
        }

        boolean permanent = minutes == -1;
        if (minutes == 0 || minutes < -1) {
            sender.sendMessage("§cCzas bana musi wynosić co najmniej minutę albo -1.");
            return true;
        }
        if (permanent && !sender.hasPermission("aisector.ban.perm")) {
            sender.sendMessage("§cPerm ban wymaga aisector.ban.perm.");
            return true;
        }
        long maxMinutes = 3L * 24 * 60;
        if (!permanent && minutes > maxMinutes && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage("§cMaksymalnie 3 dni bez aisector.ban.long.");
            return true;
        }
        if (!permanent && minutes > (Long.MAX_VALUE - System.currentTimeMillis()) / 60_000L) {
            sender.sendMessage("§cPodany czas bana jest zbyt długi.");
            return true;
        }

        String bannedBy = sender instanceof Player player ? player.getName() : "Konsola";
        Player onlinePlayer = Bukkit.getPlayerExact(target);

        if (NetworkAddressUtil.looksLikeIpLiteral(target)) {
            Optional<String> normalizedIp = NetworkAddressUtil.normalizeIpLiteral(target);
            if (normalizedIp.isEmpty()) {
                sender.sendMessage("§cPodaj prawidłowy adres IP.");
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

        sender.sendMessage("§7Wyszukuję ostatnie znane IP gracza...");
        NameResolver resolver = new NameResolver(plugin);
        resolver.resolveUuidAsync(target)
                .thenCompose(uuid -> uuid.map(resolver::resolveLastIpAsync)
                        .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()))
                        .thenApply(ip -> new ResolvedTarget(uuid.orElse(null), ip.orElse(null))))
                .whenComplete((resolved, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null || resolved == null || resolved.ip() == null) {
                        sender.sendMessage("§cNie można ustalić IP (gracz offline i brak last_ip). Podaj IP wprost lub gracza online.");
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
                sender.sendMessage("§cNie udało się zapisać bana IP w Redis.");
                return;
            }
            if (kickUuid != null) {
                Player online = Bukkit.getPlayer(kickUuid);
                if (online != null) {
                    online.kick(net.kyori.adventure.text.Component.text(
                            banMessage(displayName, bannedBy, reason, expiresAt)));
                }
            }
            sender.sendMessage("§aZbanowano IP " + displayName + " na "
                    + (permanent ? "permanentnie" : (minutes + " min")) + ".");
        }));
    }

    private String banMessage(String name, String by, String reason, long expiresAt) {
        String time = expiresAt == -1 ? "permanentnie" : "do " + java.time.Instant.ofEpochMilli(expiresAt);
        return "§cZostałeś zbanowany IP!\n§7Nick: §f" + name + "\n§7Powód: §f" + reason
                + "\n§7Przez: §f" + by + "\n§7Czas: §f" + time;
    }
}
