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

public class BanIpCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public BanIpCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.ban.ip")) { sender.sendMessage("§cBrak uprawnień."); return true; }
        if (args.length < 3) { sender.sendMessage("§cUżycie: /banip <nick|ip> <minuty|-1> <powód>"); return true; }

        String target = args[0];
        long minutes;
        try { minutes = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§cPodaj liczbę minut lub -1."); return true; }
        boolean isPerm = minutes == -1;
        if (isPerm && !sender.hasPermission("aisector.ban.perm")) { sender.sendMessage("§cPerm ban wymaga aisector.ban.perm"); return true; }
        long max = 3L * 24 * 60;
        if (!isPerm && minutes > max && !sender.hasPermission("aisector.ban.long")) {
            sender.sendMessage("§cMaksymalnie 3 dni bez aisector.ban.long."); return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String by = (sender instanceof Player) ? ((Player) sender).getName() : "Konsola";

        String ip = null;
        String nameShown = target;

        // Jeśli to IP wprost
        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            ip = target;
            nameShown = "(IP ban)";
        } else {
            // Spróbuj online
            Player online = Bukkit.getPlayerExact(target);
            if (online != null && online.getAddress() != null) {
                ip = online.getAddress().getAddress().getHostAddress();
                nameShown = online.getName();
            } else {
                // Spróbuj przez resolver + last_ip z bazy
                NameResolver resolver = new NameResolver(plugin);
                Optional<UUID> optUuid = resolver.resolveUuid(target);
                if (optUuid.isPresent()) {
                    nameShown = target;
                    ip = resolver.resolveLastIp(optUuid.get()).orElse(null);
                }
            }
        }

        if (ip == null) {
            sender.sendMessage("§cNie można ustalić IP (gracz offline i brak last_ip). Podaj IP wprost lub gracza online.");
            return true;
        }

        long now = System.currentTimeMillis();
        long expiresAt = isPerm ? -1 : now + minutes * 60_000L;

        try (Jedis j = plugin.getRedisManager().getJedis()) {
            String key = "ban:ip:" + ip;
            j.hset(key, "name", nameShown);
            j.hset(key, "reason", reason);
            j.hset(key, "banned_by", by);
            j.hset(key, "created_at", String.valueOf(now));
            j.hset(key, "expires_at", String.valueOf(expiresAt));
            j.hset(key, "type", "ip");
            j.sadd("bans:ips", ip);

            // Broadcast (bez ujawniania IP)
            JSONObject bc = new JSONObject();
            bc.put("type", "ip");
            bc.put("name", nameShown);
            bc.put("by", by);
            bc.put("reason", reason);
            bc.put("perm", isPerm);
            bc.put("minutes", minutes);
            j.publish("aisector:ban_broadcast", bc.toString());

            // Kick aktualnie online (jeśli był)
            Player online = Bukkit.getPlayerExact(nameShown);
            if (online != null) {
                JSONObject kick = new JSONObject();
                kick.put("uuid", online.getUniqueId().toString());
                kick.put("message", "§cZostałeś zbanowany IP!\n§7Powód: §f" + reason + "\n§7Przez: §f" + by + "\n§7Czas: §f" + (isPerm ? "permanentnie" : (minutes + " min")));
                j.publish("aisector:ban_kick", kick.toString());
                online.kick(net.kyori.adventure.text.Component.text("§cZostałeś zbanowany IP!\n§7Powód: §f" + reason));
            }
        }

        sender.sendMessage("§aZbanowano IP " + nameShown + " na " + (isPerm ? "permanentnie" : (minutes + " min")) + ".");
        return true;
    }
}
