package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

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
        long minutes;
        try { minutes = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§cPodaj liczbę minut lub -1."); return true; }

        boolean isPerm = minutes == -1;
        if (isPerm && !sender.hasPermission("aisector.ban.perm")) { sender.sendMessage("§cPerm ban wymaga: aisector.ban.perm"); return true; }
        long max = 3L * 24 * 60; // 3 dni
        if (!isPerm && minutes > max && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage("§cMaksymalnie 3 dni bez aisector.ban.long."); return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String bannedBy = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";

        NameResolver resolver = new NameResolver(plugin);
        Optional<UUID> optUuid = resolver.resolveUuid(targetNameArg);
        if (optUuid.isEmpty()) { sender.sendMessage("§cNie znaleziono gracza: " + targetNameArg); return true; }
        UUID uuid = optUuid.get();

        long now = System.currentTimeMillis();
        long expiresAt = isPerm ? -1 : now + minutes * 60_000L;

        try (Jedis j = plugin.getRedisManager().getJedis()) {
            String key = "ban:user:" + uuid;
            j.hset(key, "name", targetNameArg);
            j.hset(key, "reason", reason);
            j.hset(key, "banned_by", bannedBy);
            j.hset(key, "created_at", String.valueOf(now));
            j.hset(key, "expires_at", String.valueOf(expiresAt));
            j.hset(key, "type", "user");
            j.sadd("bans:users", uuid.toString());

            // Broadcast
            JSONObject broadcast = new JSONObject();
            broadcast.put("type", "user");
            broadcast.put("name", targetNameArg);
            broadcast.put("by", bannedBy);
            broadcast.put("reason", reason);
            broadcast.put("perm", isPerm);
            broadcast.put("minutes", minutes);
            j.publish("aisector:ban_broadcast", broadcast.toString());

            // Natychmiastowy kick na proxy
            JSONObject kick = new JSONObject();
            kick.put("uuid", uuid.toString());
            String msg = banMessage(targetNameArg, bannedBy, reason, expiresAt);
            kick.put("message", msg);
            j.publish("aisector:ban_kick", kick.toString());
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) online.kick(net.kyori.adventure.text.Component.text(
                banMessage(targetNameArg, bannedBy, reason, expiresAt)));
        sender.sendMessage("§aZbanowano " + targetNameArg + " na " + (isPerm ? "permanentnie" : (minutes + " min")) + ".");
        return true;
    }

    private String banMessage(String name, String by, String reason, long expiresAt) {
        String time = (expiresAt == -1) ? "permanentnie" : ("do " + Instant.ofEpochMilli(expiresAt));
        return "§cZostałeś zbanowany!\n§7Nick: §f" + name + "\n§7Powód: §f" + reason + "\n§7Przez: §f" + by + "\n§7Czas: §f" + time;
    }
}
