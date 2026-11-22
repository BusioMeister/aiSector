package ai.aisector.ranking;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.utils.BukkitTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

public final class AdminStatsCommand implements TabExecutor {
    public static final String PERMISSION = "aisector.astats";
    private final SectorPlugin plugin;

    public AdminStatsCommand(SectorPlugin plugin) { this.plugin = plugin; }

    public static boolean isBypassCommand(String command) {
        String[] words = command.strip().split("\\s+", 3);
        String name = words[0].replaceFirst("^/", "");
        return (name.equalsIgnoreCase("astats") || name.equalsIgnoreCase("aisector:astats"))
                && words.length >= 2 && words[1].equalsIgnoreCase("bypasscommand");
    }

    private void bypass(CommandSender sender, String[] args) {
        Player target = args.length == 1 || args.length == 2 && isSwitch(args[1])
                ? (sender instanceof Player p ? p : null)
                : Bukkit.getPlayerExact(args[1]);
        String mode = args.length == 3 ? args[2] : args.length == 2 && isSwitch(args[1]) ? args[1] : null;
        if (args.length > 3 || mode != null && !isSwitch(mode)) {
            sender.sendMessage(Messages.text("combat.bypass-usage"));
            return;
        }
        var users = plugin.getUserManager();
        User user = target == null ? null : users.getUser(target);
        if (user == null) {
            sender.sendMessage(Messages.text("admin-stats-command.gracz-musi-byc-na-tym-sektorze-z"));
            return;
        }
        boolean enabled = mode == null ? !user.hasCombatBypass() : mode.equalsIgnoreCase("on");
        users.saveCombatBypass(target.getUniqueId(), enabled).whenComplete((ignored, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (error != null) {
                        sender.sendMessage(Messages.text("admin-stats-command.nie-udalo-sie-potwierdzic-zapisu-statystyk"));
                        return;
                    }
                    if (Bukkit.getPlayer(target.getUniqueId()) == target && users.getUser(target) == user) {
                        user.setCombatBypass(enabled);
                        plugin.refreshCombatBypass(target);
                    }
                    sender.sendMessage(Messages.text(enabled ? "combat.bypass-on" : "combat.bypass-off", target.getName()));
                    plugin.getLogger().info(sender.getName() + " set combat bypass for " + target.getName() + " to " + enabled);
                }));
    }

    private static boolean isSwitch(String value) {
        return value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off");
    }

    enum Stat {
        KILLS(User::getKills, User::setKills, Integer.MAX_VALUE),
        DEATHS(User::getDeaths, User::setDeaths, Integer.MAX_VALUE),
        RANKING(User::getRating, User::setRating, Rating.MAX),
        KILLSTREAK(User::getKillstreak, User::setKillstreak, Rating.MAX);

        final ToIntFunction<User> read;
        final ObjIntConsumer<User> write;
        final int max;

        Stat(ToIntFunction<User> read, ObjIntConsumer<User> write, int max) {
            this.read = read;
            this.write = write;
            this.max = max;
        }

        int updated(String operation, int before, int input) {
            return (int) Math.clamp("add".equals(operation) ? (long) before + input : input, 0, max);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("bypasscommand")) {
            bypass(sender, args);
            return true;
        }
        if (args.length != 1 && args.length != 4) {
            sender.sendMessage(Messages.text("admin-stats-command.statystyki-set-ustaw-add-zmien-np-1"));
            sender.sendMessage(Messages.text("admin-stats-command.astats-nick-podglad"));
            sender.sendMessage(Messages.text("admin-stats-command.astats-nick-add-kills-1-dodaj-zabojstwo"));
            sender.sendMessage(Messages.text("admin-stats-command.astats-nick-add-deaths-1-odejmij-smierc"));
            sender.sendMessage(Messages.text("admin-stats-command.astats-nick-set-ranking-1250-ustaw-punkty"));
            sender.sendMessage(Messages.text("admin-stats-command.astats-nick-set-killstreak-5-ustaw-serie"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        var users = plugin.getUserManager();
        User user = target == null ? null : users.getUser(target);
        if (user == null || users.isTransferPending(target)) {
            sender.sendMessage(Messages.text("admin-stats-command.gracz-musi-byc-na-tym-sektorze-z"));
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(Messages.text("admin-stats-command.zabojstwa-smierci-ranking-killstreak", target.getName(), user.getKills(), user.getDeaths(), user.getRating(), user.getKillstreak()));
            return true;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        Stat stat;
        int value;
        try {
            if (!operation.equals("set") && !operation.equals("add")) throw new IllegalArgumentException();
            stat = Stat.valueOf(args[2].toUpperCase(Locale.ROOT));
            value = Integer.parseInt(args[3]);
            if (operation.equals("set") && (value < 0 || value > stat.max)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            sender.sendMessage(Messages.text("admin-stats-command.uzyj-set-lub-add-poprawnej-statystyki-i"));
            return true;
        }
        int old = stat.read.applyAsInt(user);
        int next = stat.updated(operation, old, value);
        stat.write.accept(user, next);
        String audit = sender.getName() + " edited " + target.getName() + " " + stat + ": " + old + " -> " + next;
        users.saveCombatStats(target, user).whenComplete((ignored, error) -> {
            if (error == null) plugin.getLogger().info(audit);
            BukkitTaskScheduler.execute(plugin, () -> sender.sendMessage(error == null
                    ? Messages.text("admin-stats-command.zapisano", target.getName(), args[2], old, next)
                    : Messages.text("admin-stats-command.nie-udalo-sie-potwierdzic-zapisu-statystyk")));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) return List.of();
        if (args[0].equalsIgnoreCase("bypasscommand") && args.length > 1) {
            var values = new java.util.ArrayList<>(List.of("on", "off"));
            if (args.length == 2) Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                    .map(Player::getName).forEach(values::add);
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return args.length > 3 ? List.of() : values.stream()
                    .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        List<String> values = switch (args.length) {
            case 1 -> java.util.stream.Stream.concat(java.util.stream.Stream.of("bypasscommand"), Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                    .map(Player::getName)).toList();
            case 2 -> List.of("set", "add");
            case 3 -> List.of("kills", "deaths", "ranking", "killstreak");
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
