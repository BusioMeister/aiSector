package ai.aisector.commands;

import ai.aisector.utils.Messages;

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
            sender.sendMessage(Messages.text("unban-command.brak-uprawnien"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.text("unban-command.uzycie-unban"));
            return true;
        }

        String target = args[0];
        if (NetworkAddressUtil.looksLikeIpLiteral(target)) {
            Optional<String> normalizedIp = NetworkAddressUtil.normalizeIpLiteral(target);
            if (normalizedIp.isEmpty()) {
                sender.sendMessage(Messages.text("unban-command.podaj-prawidlowy-adres-ip"));
                return true;
            }
            removeBan(sender, normalizedIp.get(), true, normalizedIp.get());
            return true;
        }

        new NameResolver(plugin).resolveUuidAsync(target).whenComplete((uuid, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof org.bukkit.entity.Player player && !player.isOnline()) return;
                    if (error != null || uuid == null || uuid.isEmpty()) {
                        sender.sendMessage(Messages.text("unban-command.nie-znaleziono-gracza", target));
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
                sender.sendMessage(Messages.text("unban-command.nie-udalo-sie-usunac-bana-z-redis"));
                plugin.getLogger().warning("Could not unban " + display + ": " + error.getMessage());
            } else if (removed == 0L) {
                sender.sendMessage(Messages.text("unban-command.nie-znaleziono-aktywnego-bana-dla", display));
            } else {
                sender.sendMessage(Messages.text("unban-command.zdjeto-bana", display));
            }
        }));
    }
}
