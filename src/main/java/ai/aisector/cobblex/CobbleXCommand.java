package ai.aisector.cobblex;

import ai.aisector.SectorPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Random;

public class CobbleXCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final Random rnd = new Random();

    public CobbleXCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players."); return true; }
        Player p = (Player) sender;

        int needed = 9 * 64;
        int have = 0;
        for (ItemStack is : p.getInventory().all(Material.COBBLESTONE).values()) {
            have += is.getAmount();
        }
        if (have < needed) {
            p.sendMessage("§cPotrzebujesz 9×64 Cobblestone (576), masz: " + have + ".");
            return true;
        }

        // Zabierz dokładnie 576 cobble
        int toRemove = needed;
        for (Map.Entry<Integer, ? extends ItemStack> entry : p.getInventory().all(Material.COBBLESTONE).entrySet()) {
            ItemStack stack = entry.getValue();
            int take = Math.min(stack.getAmount(), toRemove);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                p.getInventory().setItem(entry.getKey(), null);
            }
            toRemove -= take;
            if (toRemove <= 0) break;
        }

        int amount = 1 + rnd.nextInt(3); // 1..3
        p.getInventory().addItem(ai.aisector.cobblex.CobbleXItems.createCobbleX(plugin, amount));
        p.sendMessage("§aOtrzymano §2" + amount + "× CobbleX.");
        return true;
    }
}
