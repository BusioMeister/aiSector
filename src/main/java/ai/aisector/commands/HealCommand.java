package ai.aisector.commands;

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
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        if (args.length == 0) {
            // Leczenie samego siebie
            if (!(sender instanceof Player)) {
                sender.sendMessage("Musisz podać nick gracza, którego chcesz uleczyć.");
                return true;
            }
            Player player = (Player) sender;
            healPlayer(player);
            player.sendMessage("§aZostałeś uleczony.");
            return true;
        }

        // Leczenie innego gracza
        if (!sender.hasPermission("aisector.command.heal.others")) {
            sender.sendMessage("§cNie masz uprawnień, aby leczyć innych.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cGracz o tym nicku nie jest online.");
            return true;
        }

        healPlayer(target);
        target.sendMessage("§aZostałeś uleczony przez " + sender.getName() + ".");
        sender.sendMessage("§aPomyślnie uleczyłeś gracza " + target.getName() + ".");
        return true;
    }

    private void healPlayer(Player player) {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setFireTicks(0);
    }
}