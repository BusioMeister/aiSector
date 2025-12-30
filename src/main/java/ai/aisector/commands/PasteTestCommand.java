package ai.aisector.commands;

import ai.aisector.utils.SchematicPaster;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PasteTestCommand implements CommandExecutor {

    private final SchematicPaster paster;

    public PasteTestCommand(SchematicPaster paster) {
        this.paster = paster;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz.");
            return true;
        }

        if (args.length < 1) return false; // pokaże usage z plugin.yml [web:132]

        String schemName = args[0];
        boolean pasteAir = args.length >= 2 && args[1].equalsIgnoreCase("air");

        try {
            // Jeśli chcesz obsłużyć "air", to w SchematicPaster dodaj parametr
            // i przełącz ignoreAirBlocks(!pasteAir). Na razie: zawsze ignoruje air.
            paster.pasteSchem(player.getLocation(), schemName);

            player.sendMessage(ChatColor.GREEN + "Wklejono schem: " + schemName
                    + (pasteAir ? " (z air)" : " (bez air)"));
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Błąd: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }
}
