package ai.aisector.commands;

import ai.aisector.utils.Messages;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

public class CraftingCommand implements CommandExecutor {

    // Opcjonalne: wymagane uprawnienie
    private static final String PERM = "aisector.command.crafting";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.text("crafting-command.ta-komenda-jest-tylko-dla-graczy"));
            return true;
        }
        Player p = (Player) sender;

        if (PERM != null && !PERM.isEmpty() && !p.hasPermission(PERM)) {
            p.sendMessage(Messages.text("crafting-command.nie-masz-uprawnien-do-tej-komendy"));
            return true;
        }

        // 1) Najprościej: wbudowany interfejs stołu (działa bez bloku)
        p.openWorkbench(p.getLocation(), true);
        // Alternatywa: p.openInventory(Bukkit.createInventory(p, InventoryType.WORKBENCH));

        return true;
    }
}
