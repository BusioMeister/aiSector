package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import ai.aisector.utils.BukkitTaskScheduler;

import java.util.UUID;

public class PasteTestCommand implements CommandExecutor {

    private final SectorPlugin plugin;

    public PasteTestCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.pastetest")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz.");
            return true;
        }

        if (args.length < 1) return false; // pokaże usage z plugin.yml [web:132]

        String schemName = args[0];
        if (!schemName.matches("[A-Za-z0-9_-]{1,64}(?:\\.schem)?")) {
            sender.sendMessage(ChatColor.RED + "Nieprawidłowa nazwa schematu.");
            return true;
        }
        boolean pasteAir = args.length >= 2 && args[1].equalsIgnoreCase("air");
        UUID playerUuid = player.getUniqueId();
        Location location = player.getLocation().clone();
        player.sendMessage(ChatColor.GRAY + "Wczytuję schematic...");
        plugin.pasteSchematicAsync(location, schemName).whenComplete((ignored, error) ->
                BukkitTaskScheduler.execute(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(playerUuid);
                    if (currentPlayer == null || !currentPlayer.isOnline()) return;
                    if (error != null) {
                        currentPlayer.sendMessage(ChatColor.RED + "Błąd: " + error.getMessage());
                        plugin.getLogger().warning("Schematic paste failed for " + schemName + ": "
                                + error.getMessage());
                    } else {
                        currentPlayer.sendMessage(ChatColor.GREEN + "Wklejono schem: " + schemName
                                + (pasteAir ? " (z air)" : " (bez air)"));
                    }
                }));
        return true;
    }
}
