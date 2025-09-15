package ai.aisector.commands;

import ai.aisector.sectors.player.GlobalPlayerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.List;
import java.util.stream.Collectors;

public class TpTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            // Filtruj globalną listę graczy z naszego managera
            return GlobalPlayerManager.getGlobalPlayerList().stream()
                    .filter(name -> name.toLowerCase().startsWith(partialName))
                    .collect(Collectors.toList());
        }
        return null; // Zwróć null, aby Minecraft pokazał domyślną listę graczy online na tym serwerze
    }
}