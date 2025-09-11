package ai.aisector.commands;

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
            sender.sendMessage("Ta komenda musi być wykonana przez gracza.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("aisector.command.naprawkilof")) {
            player.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Sprawdzamy, czy gracz trzyma kilof
        if (itemInHand.getType() == Material.AIR || !itemInHand.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage("§cMusisz trzymać kilof w ręku, aby go naprawić!");
            return true;
        }

        // Sprawdzamy, czy kilof wymaga naprawy
        ItemMeta meta = itemInHand.getItemMeta();
        if (!(meta instanceof Damageable) || !((Damageable) meta).hasDamage()) {
            player.sendMessage("§eTen kilof jest już w pełni naprawiony.");
            return true;
        }

        // Sprawdzamy, czy gracz ma wystarczająco diamentów
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), DIAMOND_COST)) {
            player.sendMessage("§cNie masz wystarczająco diamentów! Potrzebujesz §e" + DIAMOND_COST + "§c.");
            return true;
        }

        // Zabieramy diamenty
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, DIAMOND_COST));

        // Naprawiamy kilof
        Damageable damageable = (Damageable) meta;
        damageable.setDamage(0);
        itemInHand.setItemMeta(meta);

        player.sendMessage("§aTwój kilof został naprawiony za §e" + DIAMOND_COST + " diamentów§a!");
        return true;
    }
}