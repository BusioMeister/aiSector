package ai.aisector.commands;

import ai.aisector.utils.Messages;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class NaprawKilofCommand implements CommandExecutor {

    private static final int DIAMOND_COST = 16;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("napraw-kilof-command.ta-komenda-musi-byc-wykonana-przez-gracza"));
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("aisector.command.naprawkilof")) {
            player.sendMessage(Messages.text("napraw-kilof-command.nie-masz-uprawnien-do-tej-komendy"));
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Sprawdzamy, czy gracz trzyma kilof
        if (itemInHand.getType() == Material.AIR || !itemInHand.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage(Messages.text("napraw-kilof-command.musisz-trzymac-kilof-w-reku-aby-go"));
            return true;
        }

        // Sprawdzamy, czy kilof wymaga naprawy
        ItemMeta meta = itemInHand.getItemMeta();
        if (!(meta instanceof Damageable) || !((Damageable) meta).hasDamage()) {
            player.sendMessage(Messages.text("napraw-kilof-command.ten-kilof-jest-juz-w-pelni-naprawiony"));
            return true;
        }

        // Sprawdzamy, czy gracz ma wystarczająco diamentów
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), DIAMOND_COST)) {
            player.sendMessage(Messages.text("napraw-kilof-command.nie-masz-wystarczajaco-diamentow-potrzebujesz", DIAMOND_COST));
            return true;
        }

        // Zabieramy diamenty
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, DIAMOND_COST));

        // Naprawiamy kilof
        Damageable damageable = (Damageable) meta;
        damageable.setDamage(0);
        itemInHand.setItemMeta(meta);

        player.sendMessage(Messages.text("napraw-kilof-command.twoj-kilof-zostal-naprawiony-za-diamentow", DIAMOND_COST));
        return true;
    }
}