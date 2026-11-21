package ai.aisector.commands;

import ai.aisector.utils.Messages;
import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.user.PlayerFlight;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    public FlyCommand(SectorPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.fly")) {
            player.sendMessage(Messages.text("fly-command.nie-masz-uprawnien"));
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("fly-command.wystapil-blad-wczytywania-twoich-danych"));
            return true;
        }
        user.setFlying(!user.isFlying());
        PlayerFlight.resolve(player.getGameMode(), user.isFlying(), user.isFlying()).apply(player);
        if (user.isFlying()) {
            player.sendMessage(Messages.text("fly-command.latanie-zostalo-wlaczone"));
        } else {
            player.sendMessage(Messages.text("fly-command.przelacznik-fly-wylaczony-tryb-gry-nadal-moze"));
        }
        return true;
    }
}
