package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import redis.clients.jedis.Jedis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class PlayerDataListener implements Listener {
    private final SectorPlugin plugin;
    private final Gson gson = new Gson();

    public PlayerDataListener(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try (Jedis jedis = plugin.getRedisManager().getJedis()) {
            String playerDataKey = "player:data:" + player.getUniqueId();
            String playerData = jedis.get(playerDataKey);

            if (playerData != null) {
                jedis.del(playerDataKey);

                Type type = new TypeToken<Map<String, Object>>() {
                }.getType();
                Map<String, Object> data = gson.fromJson(playerData, type);

                // Kompletna logika wczytywania danych
                Location loc = new Location(
                        Bukkit.getWorld((String) data.get("world")),
                        ((Number) data.get("x")).doubleValue(),
                        ((Number) data.get("y")).doubleValue(),
                        ((Number) data.get("z")).doubleValue(),
                        ((Number) data.get("yaw")).floatValue(),
                        ((Number) data.get("pitch")).floatValue()
                );

                player.setHealth(((Number) data.get("health")).doubleValue());
                player.setFoodLevel(((Number) data.get("hunger")).intValue());
                player.setSaturation(((Number) data.get("saturation")).floatValue());
                player.setTotalExperience(((Number) data.get("experience")).intValue());
                player.setLevel(((Number) data.get("level")).intValue());
                player.setExp(((Number) data.get("exp")).floatValue());
                player.setGameMode(GameMode.valueOf((String) data.get("gameMode")));

                try {
                    player.getInventory().setContents(itemStackArrayFromBase64((String) data.get("inventory")));
                    player.getInventory().setArmorContents(itemStackArrayFromBase64((String) data.get("armor")));
                    player.getInventory().setItemInOffHand(itemStackFromBase64((String) data.get("offhand")));
                } catch (IOException e) {
                    plugin.getLogger().severe("Nie udało się wczytać ekwipunku dla gracza " + player.getName());
                    e.printStackTrace();
                }

                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                player.addPotionEffects(deserializePotionEffects((List<Map<String, Object>>) data.get("effects")));

                player.teleport(loc);

                // 🔥 NOWA, POPRAWIONA LOGIKA ZGODNIE Z SUGESTIĄ BIFU 🔥
                player.setInvulnerable(true); // Ustawiamy gracza jako nietykalnego

                // Planujemy zadanie, które wyłączy nietykalność po 5 sekundach (100 ticków)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Sprawdzamy, czy gracz nadal jest online
                    if (player.isOnline()) {
                        User user = plugin.getUserManager().getUser(player);
                        // Wyłączamy nietykalność tylko wtedy, gdy gracz nie ma permanentnie włączonego trybu /god
                        if (user != null && !user.isGodMode()) {
                            player.setInvulnerable(false);
                        }
                    }
                }, 100L);

                plugin.getLogger().info("Pomyślnie wczytano dane transferowe dla " + player.getName());
            }
        }
    }

    private Set<PotionEffect> deserializePotionEffects(List<Map<String, Object>> serializedEffects) {
        Set<PotionEffect> effects = new HashSet<>();
        if (serializedEffects == null) return effects;
        for (Map<String, Object> effectData : serializedEffects) {
            PotionEffectType type = PotionEffectType.getByName((String) effectData.get("type"));
            if (type != null) {
                effects.add(new PotionEffect(
                        type,
                        ((Number) effectData.get("duration")).intValue(),
                        ((Number) effectData.get("amplifier")).intValue()
                ));
            }
        }
        return effects;
    }

    private ItemStack itemStackFromBase64(String data) throws IOException {
        if (data == null) return new ItemStack(Material.AIR);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        if (data == null) return new ItemStack[0];
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}