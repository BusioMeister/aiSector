package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class CoreCommand implements TabExecutor {
    private static final String PERMISSION = "aisector.core.reload";
    private final SectorPlugin plugin;
    private final String instance = UUID.randomUUID().toString();

    public CoreCommand(SectorPlugin plugin) { this.plugin = plugin; }

    public void subscribe() {
        plugin.getRedisManager().subscribe(new redis.clients.jedis.JedisPubSub() {
            @Override public void onMessage(String channel, String request) {
                if (!Messages.RELOAD_CHANNEL.equals(channel) || request == null || request.length() != 36
                        || request.equals(instance)) return;
                try {
                    UUID.fromString(request);
                    Messages.reload();
                } catch (IllegalArgumentException | IOException error) {
                    plugin.getLogger().warning("Could not apply network message reload: " + error.getMessage());
                }
            }
        }, Messages.RELOAD_CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Messages.text("core.no-permission"));
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Messages.text("core.usage"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Messages.reload();
            } catch (IOException error) {
                BukkitTaskScheduler.execute(plugin, () -> sender.sendMessage(Messages.text("core.failed", error.getMessage())));
                return;
            }
            plugin.getRedisManager().publishAsync(Messages.RELOAD_CHANNEL, instance).whenComplete((count, error) ->
                    BukkitTaskScheduler.execute(plugin, () -> sender.sendMessage(error == null
                            ? Messages.text("core.success") : Messages.text("core.local-only"))));
        });
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission(PERMISSION) && args.length == 1
                && "reload".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("reload") : List.of();
    }
}
