package ai.aisector.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class InvseeCommand implements CommandExecutor {

    public InvseeCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!sender.hasPermission("aisector.command.invsee")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUżycie: /invsee <gracz>");
            return true;
        }

        Player admin = (Player) sender;
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            admin.sendMessage("§cGracz o nicku '" + targetName + "' nie jest online na tym sektorze.");
            return true;
        }

        // 🔥 ZMIANA: Zmniejszamy GUI do 5 rzędów (45 slotów)
        Inventory invseeGui = Bukkit.createInventory(null, 45, "§8Ekwipunek gracza: §c" + targetPlayer.getName());
        PlayerInventory targetInventory = targetPlayer.getInventory();

        // Układamy przedmioty w nowym, kompaktowym porządku
        // Główny ekwipunek (sloty 9-35 gracza) -> do GUI 0-26
        for (int i = 9; i <= 35; i++) {
            invseeGui.setItem(i - 9, targetInventory.getItem(i));
        }
        // Pasek szybkiego dostępu (sloty 0-8 gracza) -> do GUI 27-35
        for (int i = 0; i < 9; i++) {
            invseeGui.setItem(i + 27, targetInventory.getItem(i));
        }

        // Zbroja i lewa ręka w ostatnim rzędzie (sloty 36-44)
        invseeGui.setItem(36, targetInventory.getHelmet());
        invseeGui.setItem(37, targetInventory.getChestplate());
        invseeGui.setItem(38, targetInventory.getLeggings());
        invseeGui.setItem(39, targetInventory.getBoots());
        invseeGui.setItem(40, targetInventory.getItemInOffHand());

        // Wypełniamy resztę ostatniego rzędu ozdobnymi szarymi szybkami
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        // Upewnij się, że szybka nie ma nazwy
        if (filler.hasItemMeta()) {
            filler.getItemMeta().setDisplayName(" ");
        }
        for (int i = 41; i <= 44; i++) {
            invseeGui.setItem(i, filler);
        }

        admin.openInventory(invseeGui);
        return true;
    }
}