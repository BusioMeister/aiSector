package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.player.VanishManager;
import ai.aisector.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final VanishManager vanishManager;

    public VanishCommand(SectorPlugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.vanish")) {
            player.sendMessage(Messages.text("vanish-command.nie-masz-uprawnien"));
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("vanish-command.wystapil-blad-wczytywania-twoich-danych"));
            return true;
        }

        // Zmień stan w obiekcie User (aby zapisał się w bazie)
        user.setVanished(!user.isVanished());

        // Poproś VanishManager o wykonanie akcji
        if (user.isVanished()) {
            vanishManager.vanish(player);
            // po vanish/unvanish:
            plugin.getGuildTagManager().updateTagsFor(player);

            player.sendMessage(Messages.text("vanish-command.vanish-zostal-wlaczony"));
        } else {
            vanishManager.unvanish(player);
            // po vanish/unvanish:
            plugin.getGuildTagManager().updateTagsFor(player);

            player.sendMessage(Messages.text("vanish-command.vanish-zostal-wylaczony"));
        }
        return true;
    }
}