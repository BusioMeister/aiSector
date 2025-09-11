package ai.aisector.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class InventorySerializer {

    private static final Gson gson = new Gson();
    private static final Type type = new TypeToken<Map<Integer, String>>() {}.getType();

    public static String serializeInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        Map<Integer, String> serialized = new HashMap<>();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) serialized.put(i, itemStackToBase64(contents[i]));
        }
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) serialized.put(100 + i, itemStackToBase64(armor[i]));
        }
        if (offhand != null && offhand.getType() != Material.AIR) {
            serialized.put(104, itemStackToBase64(offhand));
        }
        return gson.toJson(serialized);
    }

    public static void deserializeAndUpdateInventory(org.bukkit.inventory.PlayerInventory playerInventory, String json) {
        Map<Integer, String> serialized = gson.fromJson(json, type);
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = new ItemStack(Material.AIR);

        for (Map.Entry<Integer, String> entry : serialized.entrySet()) {
            int slot = entry.getKey();
            try {
                ItemStack item = itemStackFromBase64(entry.getValue());
                if (slot >= 0 && slot < 36) {
                    contents[slot] = item;
                } else if (slot >= 100 && slot < 104) {
                    armor[slot - 100] = item;
                } else if (slot == 104) {
                    offhand = item;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerInventory.setContents(contents);
        playerInventory.setArmorContents(armor);
        playerInventory.setItemInOffHand(offhand);
    }

    public static Inventory createGuiFromJson(String json, String title) throws IOException {
        // Używamy 5 rzędów (45 slotów)
        Inventory gui = Bukkit.createInventory(null, 45, title);
        Map<Integer, String> serialized = gson.fromJson(json, type);

        for (Map.Entry<Integer, String> entry : serialized.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = itemStackFromBase64(entry.getValue());

            if (slot >= 9 && slot <= 35) {
                // Główny ekwipunek -> w GUI sloty 0-26
                gui.setItem(slot - 9, item);
            } else if (slot >= 0 && slot < 9) {
                // Pasek szybkiego dostępu -> w GUI sloty 27-35
                gui.setItem(slot + 27, item);
            } else if (slot == 103) { // Hełm
                gui.setItem(36, item);
            } else if (slot == 102) { // Napierśnik
                gui.setItem(37, item);
            } else if (slot == 101) { // Spodnie
                gui.setItem(38, item);
            } else if (slot == 100) { // Buty
                gui.setItem(39, item);
            } else if (slot == 104) { // Lewa ręka
                gui.setItem(40, item);
            }
        }
        return gui;
    }
    public static String itemStackToBase64(ItemStack item) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static ItemStack itemStackFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}