package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import org.bukkit.command.*;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.Set;

public class BanListCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public BanListCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try (Jedis j = plugin.getRedisManager().getJedis()) {
            sender.sendMessage("§6Aktywne bany graczy:");
            Set<String> users = j.smembers("bans:users");
            for (String u : users) {
                String key = "ban:user:" + u;
                String name = j.hget(key, "name");
                String reason = j.hget(key, "reason");
                String by = j.hget(key, "banned_by");
                long exp = Long.parseLong(j.hget(key, "expires_at"));
                sender.sendMessage("§e" + name + " §7(" + (exp == -1 ? "perm" : Instant.ofEpochMilli(exp)) + ") §8- §f" + reason + " §8by §f" + by);
            }
            sender.sendMessage("§6Aktywne bany IP:");
            Set<String> ips = j.smembers("bans:ips");
            for (String ip : ips) {
                String key = "ban:ip:" + ip;
                String name = j.hget(key, "name");
                String reason = j.hget(key, "reason");
                String by = j.hget(key, "banned_by");
                long exp = Long.parseLong(j.hget(key, "expires_at"));
                sender.sendMessage("§e" + name + " §7(IP) §7(" + (exp == -1 ? "perm" : Instant.ofEpochMilli(exp)) + ") §8- §f" + reason + " §8by §f" + by);
            }
        }
        return true;
    }
}
