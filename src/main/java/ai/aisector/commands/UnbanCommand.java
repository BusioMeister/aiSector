package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import org.bukkit.command.*;
import redis.clients.jedis.Jedis;

import java.util.Optional;
import java.util.UUID;

public class UnbanCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public UnbanCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aisector.unban")) { sender.sendMessage("§cBrak uprawnień."); return true; }
        if (args.length < 1) { sender.sendMessage("§cUżycie: /unban <nick|ip>"); return true; }
        String target = args[0];

        try (Jedis j = plugin.getRedisManager().getJedis()) {
            if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                j.del("ban:ip:" + target);
                j.srem("bans:ips", target);
                sender.sendMessage("§aZdjęto bana IP: " + target);
            } else {
                NameResolver resolver = new NameResolver(plugin);
                Optional<UUID> opt = resolver.resolveUuid(target);
                if (opt.isEmpty()) { sender.sendMessage("§cNie znaleziono gracza: " + target); return true; }
                String uuid = opt.get().toString();
                j.del("ban:user:" + uuid);
                j.srem("bans:users", uuid);
                sender.sendMessage("§aZdjęto bana: " + target);
            }
        }
        return true;
    }
}
