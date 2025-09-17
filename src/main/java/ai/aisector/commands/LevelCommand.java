package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.user.User;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LevelCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final MiningLevelManager levelManager;

    public LevelCommand(SectorPlugin plugin, MiningLevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        User user = plugin.getUserManager().getUser(player);

        if (user == null) return true;

        int currentLevel = user.getMiningLevel();
        long currentXp = user.getMiningExperience();
        long requiredXp = levelManager.getExperienceForNextLevel(currentLevel);

        player.sendMessage("§m                                        ");
        player.sendMessage("§7Twoje Statystyki Kopania:");
        player.sendMessage("§fAktualnie posiadasz §e" + currentXp + " pkt §fczyli §a" + currentLevel + " poziom!");
        if (currentLevel < 100) {
            player.sendMessage("§fDo następnego poziomu brakuje Ci: §c" + (requiredXp - currentXp) + " pkt");
        } else {
            player.sendMessage("§aOsiągnąłeś maksymalny poziom!");
        }
        player.sendMessage(" ");
        player.sendMessage("§7Wykopany kamień: §e" + user.getMinedBlockCount(Material.STONE));
        player.sendMessage("§7Wykopany obsydian: §e" + user.getMinedBlockCount(Material.OBSIDIAN));
        player.sendMessage("§m                                        ");

        return true;
    }
}