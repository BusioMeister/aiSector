package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.resolver.NameResolver;
import ai.aisector.shared.NetworkAddressUtil;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public final class UnbanCommand implements CommandExecutor {
    private final SectorPlugin plugin;

    public UnbanCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.unban")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUżycie: /unban <nick|ip>");
            return true;
        }

        String target = args[0];
        if (NetworkAddressUtil.looksLikeIpLiteral(target)) {
            Optional<String> normalizedIp = NetworkAddressUtil.normalizeIpLiteral(target);
            if (normalizedIp.isEmpty()) {
                sender.sendMessage("§cPodaj prawidłowy adres IP.");
                return true;
            }
            removeBan(sender, normalizedIp.get(), true, normalizedIp.get());
            return true;
        }

        new NameResolver(plugin).resolveUuidAsync(target).whenComplete((uuid, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
                    if (error != null || uuid == null || uuid.isEmpty()) {
                        sender.sendMessage("§cNie znaleziono gracza: " + target);
                        return;
                    }
                    removeBan(sender, uuid.get().toString(), false, target);
                }));
        return true;
    }

    private void removeBan(CommandSender sender, String key, boolean ip, String display) {
        String hashKey = (ip ? "ban:ip:" : "ban:user:") + key;
        String indexKey = ip ? "bans:ips" : "bans:users";
        plugin.getRedisManager().executeAsync(jedis -> {
            return BanRedisScripts.remove(jedis, hashKey, indexKey, key);
        }).whenComplete((removed, error) -> BukkitTaskScheduler.execute(plugin, () -> {
            if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
            if (error != null) {
                sender.sendMessage("§cNie udało się usunąć bana z Redis.");
                plugin.getLogger().warning("Could not unban " + display + ": " + error.getMessage());
            } else if (removed == 0L) {
                sender.sendMessage("§cNie znaleziono aktywnego bana dla: " + display);
            } else {
                sender.sendMessage("§aZdjęto bana: " + display);
            }
        }));
    }
}
