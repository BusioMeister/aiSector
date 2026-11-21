package ai.aisector.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

public class InventorySerializer {

    private static final Gson gson = new Gson();
    private static final Type type = new TypeToken<Map<Integer, String>>() {}.getType();
    private static final int MAX_SERIALIZED_INVENTORY_CHARS = 1_048_576;
    private static final int MAX_SERIALIZED_ITEM_BYTES = 262_144;

    public static final class PreparedInventory {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        private PreparedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
        }

        public void applyTo(org.bukkit.inventory.PlayerInventory inventory) {
            inventory.setContents(contents);
            inventory.setArmorContents(armor);
            inventory.setItemInOffHand(offhand);
        }
    }

    public static Map<Integer, String> parseSerializedInventory(String json) {
        if (json == null || json.length() > MAX_SERIALIZED_INVENTORY_CHARS) {
            throw new IllegalArgumentException("Inventory JSON is empty or exceeds the 1 MiB limit");
        }
        Map<Integer, String> serialized = gson.fromJson(json, type);
        if (serialized == null || serialized.size() > 46) {
            throw new IllegalArgumentException("Invalid or oversized serialized inventory");
        }
        for (Map.Entry<Integer, String> entry : serialized.entrySet()) {
            Integer slot = entry.getKey();
            String item = entry.getValue();
            if (slot == null || item == null || item.isBlank()
                    || !((slot >= 0 && slot <= 40) || (slot >= 100 && slot <= 104))) {
                throw new IllegalArgumentException("Inventory contains an invalid slot or item");
            }
        }
        Map<Integer, String> normalized = new HashMap<>();
        serialized.forEach((slot, item) -> { if (slot < 36 || slot >= 100) normalized.put(slot, item); });
        // Historical writers passed getContents() (41 entries) AND explicit armor/offhand.
        // Prefer explicit canonical slots; retain old 36..40 aliases only as a fallback.
        serialized.forEach((slot, item) -> {
            if (slot >= 36 && slot <= 40) normalized.putIfAbsent(100 + slot - 36, item);
        });
        return Collections.unmodifiableMap(normalized);
    }

    public static String serializeInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        Map<Integer, String> serialized = new HashMap<>();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null) serialized.put(i, itemStackToBase64(contents[i]));
        }
        for (int i = 0; i < Math.min(armor.length, 4); i++) {
            if (armor[i] != null) serialized.put(100 + i, itemStackToBase64(armor[i]));
        }
        if (offhand != null && offhand.getType() != Material.AIR) {
            serialized.put(104, itemStackToBase64(offhand));
        }
        return gson.toJson(serialized);
    }

    public static void deserializeAndUpdateInventory(org.bukkit.inventory.PlayerInventory playerInventory, String json) {
        prepareInventory(json).applyTo(playerInventory);
    }

    public static PreparedInventory prepareInventory(String json) {
        Map<Integer, String> serialized = parseSerializedInventory(json);
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
            } catch (IOException | RuntimeException exception) {
                throw new IllegalArgumentException("Could not decode inventory slot " + slot, exception);
            }
        }
        return new PreparedInventory(contents, armor, offhand);
    }

    public static Inventory createGuiFromJson(String json, String title) throws IOException {
        return fillGuiFromJson(json, Bukkit.createInventory(null, 45, title));
    }

    public static Inventory fillGuiFromJson(String json, Inventory gui) throws IOException {
        // Używamy 5 rzędów (45 slotów)
        Map<Integer, String> serialized = parseSerializedInventory(json);

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
        byte[] data = item.serializeAsBytes();
        if (data.length > MAX_SERIALIZED_ITEM_BYTES) throw new IllegalStateException("Item exceeds serialized size limit");
        try { validateInflatedItemSize(data); }
        catch (IOException oversized) { throw new IllegalStateException("Item exceeds safe serialized limits", oversized); }
        return "v2:" + java.util.Base64.getEncoder().encodeToString(data);
    }

    public static ItemStack itemStackFromBase64(String data) throws IOException {
        try {
            if (data == null || data.length() > ((MAX_SERIALIZED_ITEM_BYTES + 2) / 3) * 4 + 4096) {
                throw new IOException("Serialized item exceeds the encoded size limit");
            }
            boolean version2 = data.startsWith("v2:");
            byte[] decoded = version2 ? java.util.Base64.getDecoder().decode(data.substring(3)) : Base64Coder.decodeLines(data);
            if (decoded.length == 0 || decoded.length > MAX_SERIALIZED_ITEM_BYTES) {
                throw new IOException("Serialized item exceeds the decoded size limit");
            }
            if (version2) {
                validateInflatedItemSize(decoded);
                return ItemStack.deserializeBytes(decoded);
            }
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decoded);
                 LegacyItemInputStream dataInput = new LegacyItemInputStream(inputStream)) {
                Object decodedItem = dataInput.readObject();
                if (!(decodedItem instanceof ItemStack item)) {
                    throw new IOException("Serialized object is not an ItemStack");
                }
                return item;
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        } catch (IllegalArgumentException malformedBase64) {
            throw new IOException("Invalid Base64 item data", malformedBase64);
        }
    }

    static void validateInflatedItemSize(byte[] data) throws IOException {
        if (data.length < 2 || (data[0] & 255) != 0x1f || (data[1] & 255) != 0x8b) return;
        try (var input = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 4_194_304) throw new IOException("Item exceeds the 4 MiB inflated size limit");
            }
        }
    }
}
