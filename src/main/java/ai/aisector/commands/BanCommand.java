package ai.aisector.commands;

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
        if (!sender.hasPermission("aisector.ban.use")) { sender.sendMessage("§cBrak uprawnień."); return true; }
        if (args.length < 3) { sender.sendMessage("§cUżycie: /ban <nick> <minuty|-1> <powód>"); return true; }

        String targetNameArg = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (targetNameArg.isBlank() || targetNameArg.length() > 16 || reason.length() > 256) {
            sender.sendMessage("§cNieprawidłowy nick lub powód (maksymalnie 256 znaków).");
            return true;
        }
        long minutes;
        try { minutes = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§cPodaj liczbę minut lub -1."); return true; }

        boolean isPerm = minutes == -1;
        if (minutes == 0 || minutes < -1) {
            sender.sendMessage("§cCzas bana musi wynosić co najmniej minutę albo -1.");
            return true;
        }
        if (isPerm && !sender.hasPermission("aisector.ban.perm")) { sender.sendMessage("§cPerm ban wymaga: aisector.ban.perm"); return true; }
        long max = 3L * 24 * 60; // 3 dni
        if (!isPerm && minutes > max && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage("§cMaksymalnie 3 dni bez aisector.ban.long."); return true;
        }
        if (!isPerm && minutes > (Long.MAX_VALUE - System.currentTimeMillis()) / 60_000L) {
            sender.sendMessage("§cPodany czas bana jest zbyt długi.");
            return true;
        }
        String bannedBy = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";

        sender.sendMessage("§7Wyszukuję gracza i zapisuję bana...");
        new NameResolver(plugin).resolveUuidAsync(targetNameArg).whenComplete((resolvedUuid, resolveError) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (resolveError != null || resolvedUuid == null || resolvedUuid.isEmpty()) {
                        sender.sendMessage(resolveError == null
                                ? "§cNie znaleziono gracza: " + targetNameArg
                                : "§cNie udało się wyszukać gracza.");
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
                sender.sendMessage("§cNie udało się zapisać bana w Redis.");
                return;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.kick(net.kyori.adventure.text.Component.text(banMessage(targetName, bannedBy, reason, expiresAt)));
            }
            sender.sendMessage("§aZbanowano " + targetName + " na "
                    + (permanent ? "permanentnie" : (minutes + " min")) + ".");
        }));
    }

    private String banMessage(String name, String by, String reason, long expiresAt) {
        String time = (expiresAt == -1) ? "permanentnie" : ("do " + Instant.ofEpochMilli(expiresAt));
        return "§cZostałeś zbanowany!\n§7Nick: §f" + name + "\n§7Powód: §f" + reason + "\n§7Przez: §f" + by + "\n§7Czas: §f" + time;
    }
}
