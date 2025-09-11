package ai.aisector.commands;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameModeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być używana tylko przez graczy!");
            return true;
        }

        Player player = (Player) sender;

        // --- POCZĄTEK DODANEGO KODU ---
        // Sprawdzamy, czy gracz jest operatorem LUB ma odpowiednią permisję
        if (!player.isOp() && !player.hasPermission("aisector.command.gamemode")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do tej komendy.");
            return true;
        }
        // --- KONIEC DODANEGO KODU ---

        if (command.getName().equalsIgnoreCase("gamemode") || command.getName().equalsIgnoreCase("gm")) {
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Poprawne użycie: /gamemode <creative|survival|adventure|spectator>");
                return true;
            }

            GameMode newGameMode = getGameModeFromString(args[0]);
            if (newGameMode == null) {
                player.sendMessage(ChatColor.RED + "Nieprawidłowy tryb gry. Dostępne tryby: creative, survival, adventure, spectator.");
                return true;
            }

            player.setGameMode(newGameMode);
            player.sendMessage(ChatColor.GREEN + "Zmieniłeś tryb gry na " + newGameMode.toString().toLowerCase() + ".");
            return true;
        }

        return false;
    }

    private GameMode getGameModeFromString(String modeString) {
        try {
            int modeValue = Integer.parseInt(modeString);
            switch (modeValue) {
                case 0:
                    return GameMode.SURVIVAL;
                case 1:
                    return GameMode.CREATIVE;
                case 2:
                    return GameMode.ADVENTURE;
                case 3:
                    return GameMode.SPECTATOR;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            // Używamy .name() aby dopasować do standardowych nazw trybów gry
            for (GameMode mode : GameMode.values()) {
                if (mode.name().equalsIgnoreCase(modeString)) {
                    return mode;
                }
            }
            return null;
        }
    }
}