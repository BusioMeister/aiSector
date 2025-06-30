package ai.aisector.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public class ItemStackUtil {

    // Serializacja ItemStack[] do Base64
    public static String serializeItemStackArray(ItemStack[] items) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream bukkitOutputStream = new BukkitObjectOutputStream(byteArrayOutputStream)) {

            bukkitOutputStream.writeInt(items.length); // Zapisujemy długość tablicy
            for (ItemStack item : items) {
                bukkitOutputStream.writeObject(item); // Zapisujemy każdy element
            }

            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()); // Kodujemy do Base64
        } catch (IOException e) {
            throw new RuntimeException("Nie udało się zserializować ItemStack[]", e);
        }
    }

    // Deserializacja ItemStack[] z Base64
    public static ItemStack[] deserializeItemStackArray(String base64) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream bukkitInputStream = new BukkitObjectInputStream(byteArrayInputStream)) {

            int length = bukkitInputStream.readInt(); // Odczytujemy długość tablicy
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) bukkitInputStream.readObject(); // Odczytujemy każdy element
            }

            return items;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Nie udało się zdeserializować ItemStack[]", e);
        }
    }
}
