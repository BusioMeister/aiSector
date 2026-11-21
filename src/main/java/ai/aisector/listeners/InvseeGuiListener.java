package ai.aisector.listeners;

import ai.aisector.utils.Messages;

import org.bukkit.Bukkit;
import ai.aisector.utils.PluginMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class InvseeGuiListener implements Listener {

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!PluginMenu.is(event.getInventory(), PluginMenu.Kind.INVSEE)) {
            return;
        }

        String targetName = ((PluginMenu) event.getInventory().getHolder()).target();

        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            event.getPlayer().sendMessage(Messages.text("invsee-gui-listener.gracz-wylogowal-sie-zmiany-nie-zostaly-zapisane", targetName));
            return;
        }

        ai.aisector.SectorPlugin plugin = (ai.aisector.SectorPlugin) Bukkit.getPluginManager().getPlugin("aiSECTOR");
        if (plugin != null && plugin.getUserManager().isTransferPending(targetPlayer)) {
            event.getPlayer().sendMessage(Messages.text("invsee-gui-listener.gracz-jest-w-trakcie-transferu-zmiany-nie"));
            return;
        }

        Inventory gui = event.getInventory();
        PlayerInventory targetInventory = targetPlayer.getInventory();

        // 🔥 ZMIANA: Odczytujemy przedmioty z nowego, 45-slotowego układu 🔥
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];

        // Główny ekwipunek (sloty 9-35 gracza) -> z GUI 0-26
        for (int i = 0; i < 27; i++) {
            contents[i + 9] = gui.getItem(i);
        }
        // Pasek szybkiego dostępu (sloty 0-8 gracza) -> z GUI 27-35
        for (int i = 0; i < 9; i++) {
            contents[i] = gui.getItem(i + 27);
        }

        // Zbroja -> z GUI 36-39
        armor[3] = gui.getItem(36); // Hełm
        armor[2] = gui.getItem(37); // Napierśnik
        armor[1] = gui.getItem(38); // Spodnie
        armor[0] = gui.getItem(39); // Buty

        // Lewa ręka -> z GUI 40
        ItemStack offhand = gui.getItem(40);

        targetInventory.setContents(contents);
        targetInventory.setArmorContents(armor);
        targetInventory.setItemInOffHand(offhand);

        event.getPlayer().sendMessage(Messages.text("invsee-gui-listener.zapisano-zmiany-w-ekwipunku-gracza", targetName));
    }
}
