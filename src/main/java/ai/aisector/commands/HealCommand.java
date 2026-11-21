package ai.aisector.commands;

import ai.aisector.utils.Messages;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HealCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.heal")) {
            sender.sendMessage(Messages.text("heal-command.nie-masz-uprawnien"));
            return true;
        }

        if (args.length == 0) {
            // Leczenie samego siebie
            if (!(sender instanceof Player)) {
                sender.sendMessage(Messages.text("heal-command.musisz-podac-nick-gracza-ktorego-chcesz-uleczyc"));
                return true;
            }
            Player player = (Player) sender;
            healPlayer(player);
            player.sendMessage(Messages.text("heal-command.zostales-uleczony"));
            return true;
        }

        // Leczenie innego gracza
        if (!sender.hasPermission("aisector.command.heal.others")) {
            sender.sendMessage(Messages.text("heal-command.nie-masz-uprawnien-aby-leczyc-innych"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Messages.text("heal-command.gracz-o-tym-nicku-nie-jest-online"));
            return true;
        }

        healPlayer(target);
        target.sendMessage(Messages.text("heal-command.zostales-uleczony-przez", sender.getName()));
        sender.sendMessage(Messages.text("heal-command.pomyslnie-uleczyles-gracza", target.getName()));
        return true;
    }

    private void healPlayer(Player player) {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setFireTicks(0);
    }
}