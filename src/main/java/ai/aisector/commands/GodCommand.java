package ai.aisector.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GodCommand implements CommandExecutor, Listener {

    public static final Set<UUID> gods = new HashSet<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.god")) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (gods.contains(uuid)) {
            // Wyłącz tryb boga
            gods.remove(uuid);
            player.setInvulnerable(false);
            player.sendMessage("§cTryb boga został wyłączony.");
        } else {
            // Włącz tryb boga
            gods.add(uuid);
            player.setInvulnerable(true);
            player.sendMessage("§aTryb boga został włączony.");
        }
        return true;
    }

    // Zabezpieczenie na wypadek wyjścia gracza
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gods.remove(event.getPlayer().getUniqueId());
    }
}