package ai.aisector.commands;

import ai.aisector.database.RedisManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ai.aisector.utils.BukkitTaskScheduler;

public class WyjebaneCommand implements CommandExecutor {
    private static final String TOGGLE_PRIVATE_MESSAGES_SCRIPT = "if redis.call('exists', KEYS[1]) == 1 "
            + "then redis.call('del', KEYS[1]); return 0; "
            + "else redis.call('set', KEYS[1], 'true'); return 1; end";

    private final RedisManager redisManager;
    private final JavaPlugin plugin;

    public WyjebaneCommand(JavaPlugin plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        String uuid = player.getUniqueId().toString();
        redisManager.executeAsync(jedis -> ((Number) jedis.eval(TOGGLE_PRIVATE_MESSAGES_SCRIPT,
                java.util.Collections.singletonList("pm_disabled:" + uuid), java.util.Collections.emptyList()))
                .longValue() == 1L).whenComplete((disabled, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage("§cNie udało się zmienić ustawień prywatnych wiadomości.");
                    } else if (Boolean.TRUE.equals(disabled)) {
                        player.sendMessage("§aOd teraz masz WYJEBANE!");
                    } else {
                        player.sendMessage("§cOd teraz §4NIE §cmasz WYJEBANE!");
                    }
                }));
        return true;
    }
}
