package ai.aisector.player;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import ai.aisector.utils.MessageUtil;

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

public class PlayerJoinListener implements Listener {

    private final SectorPlugin plugin;
    private final UserManager userManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public PlayerJoinListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.redisManager = plugin.getRedisManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Daj 1 tick na spójność danych z Redis
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            User user = userManager.getUser(player);
            if (user == null) {
                player.kickPlayer("§cWystąpił krytyczny błąd podczas ładowania Twojego profilu.");
                return;
            }

            try (Jedis jedis = redisManager.getJedis()) {
                String playerDataKey = "player:data:" + player.getUniqueId();
                String playerData = jedis.get(playerDataKey);

                Integer heldSlotBox = null;
                if (playerData != null) {
                    jedis.del(playerDataKey);
                    heldSlotBox = loadTransferData(player, playerData); // Zwraca odczytany heldSlot
                }

                // Obsługa finalnego celu teleportu
                String finalTargetKey = "player:final_teleport_target:" + player.getUniqueId();
                String finalTargetData = jedis.get(finalTargetKey);
                if (finalTargetData != null) {
                    jedis.del(finalTargetKey);
                    Location finalTargetLocation = locationFromJson(finalTargetData);
                    Integer finalHeld = heldSlotBox;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        player.teleport(finalTargetLocation);
                        if (finalHeld != null) applyHeldSlotLater(player, finalHeld, 2L);
                        sendWelcomePackage(player);
                    }, 2L);
                    return;
                }

                // Wymuszony spawn na sektor
                String forceSpawnKey = "player:force_sector_spawn:" + player.getUniqueId();
                String targetSectorName = jedis.get(forceSpawnKey);
                if (targetSectorName != null) {
                    jedis.del(forceSpawnKey);
                    Integer finalHeld = heldSlotBox;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        Sector targetSector = sectorManager.getSectorByName(targetSectorName);
                        if (targetSector != null) {
                            Location sectorSpawn = sectorManager.getSectorSpawnLocation(targetSector);
                            if (sectorSpawn != null) {
                                player.teleport(sectorSpawn);
                                if (finalHeld != null) applyHeldSlotLater(player, finalHeld, 2L);
                                sendWelcomePackage(player);
                                player.sendMessage("§aZostałeś przeniesiony na sektor §e" + targetSector.getName() + "§a.");
                            }
                        }
                    }, 5L);
                    return;
                }

                // Brak dodatkowych teleportów: ustaw slot po 2 tickach
                if (heldSlotBox != null) {
                    applyHeldSlotLater(player, heldSlotBox, 2L);
                }
                sendWelcomePackage(player);
            }
        }, 1L);
    }

    // Zwraca odczytany heldSlot, bez natychmiastowego ustawiania
    private Integer loadTransferData(Player player, String playerData) {
        plugin.getLogger().info("[DEBUG] Otrzymano dane dla " + player.getName() + ": " + playerData);

        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(playerData, type);

        // 1. Odczyt danych
        Location loc = locationFromJson(playerData);
        double health = ((Number) data.get("health")).doubleValue();
        int hunger = ((Number) data.get("hunger")).intValue();
        GameMode gameMode = GameMode.valueOf((String) data.get("gameMode"));
        int heldSlot = data.containsKey("heldSlot") ? ((Number) data.get("heldSlot")).intValue() : 0;
        plugin.getLogger().info("[DEBUG] Odczytano dla " + player.getName() + " HELD_SLOT=" + heldSlot);

        ItemStack[] inventoryContents = null;
        ItemStack[] armorContents = null;
        ItemStack offhandItem = null;

        try {
            inventoryContents = itemStackArrayFromBase64((String) data.get("inventory"));
            armorContents = itemStackArrayFromBase64((String) data.get("armor"));
            offhandItem = itemStackFromBase64((String) data.get("offhand"));
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się wczytać ekwipunku dla gracza " + player.getName());
        }

        Collection<PotionEffect> effects = deserializePotionEffects((List<Map<String, Object>>) data.get("effects"));

        // 2. Najpierw teleport
        player.teleport(loc);

        // 3. Wczytanie reszty w następnym ticku
        final ItemStack[] finalInventoryContents = inventoryContents;
        final ItemStack[] finalArmorContents = armorContents;
        final ItemStack finalOffhandItem = offhandItem;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            // Wartości ochronne przy health (gdy gracz ma mniejsze max HP)
            double safeHealth = Math.min(Math.max(health, 0.1D), player.getMaxHealth());
            player.setHealth(safeHealth);
            player.setFoodLevel(hunger);
            player.setGameMode(gameMode);

            if (finalInventoryContents != null) player.getInventory().setContents(finalInventoryContents);
            if (finalArmorContents != null) player.getInventory().setArmorContents(finalArmorContents);
            if (finalOffhandItem != null) player.getInventory().setItemInOffHand(finalOffhandItem);

            // Reset i załadowanie efektów
            for (PotionEffect currentEffect : player.getActivePotionEffects()) {
                player.removePotionEffect(currentEffect.getType());
            }
            if (effects != null && !effects.isEmpty()) player.addPotionEffects(effects);
        });

        // Krótka niewrażliwość
        player.setInvulnerable(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                User u = plugin.getUserManager().getUser(player);
                if (u == null || !u.isGodMode()) {
                    player.setInvulnerable(false);
                }
            }
        }, 100L);

        return heldSlot;
    }
    // JAK KURWA ZAPISAC TE JEBANE OKNO KTÓRE GRACZ TRZYMA W RĘKU NO JAK BIFU WIEM ZE TY WIESZ
    private void applyHeldSlotLater(Player player, int slot, long delay) {
        int safe = Math.max(0, Math.min(8, slot));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            try {
                player.getInventory().setHeldItemSlot(safe);
            } catch (IllegalArgumentException ex) {
                player.getInventory().setHeldItemSlot(0);
            }
        }, delay);
    }

    private void sendWelcomePackage(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Sector sector = sectorManager.getSector(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (sector != null) {
                borderManager.sendWorldBorder(player, sector);
                MessageUtil.sendTitle(player, "", "§7Połączono z sektorem §9" + sector.getName(), 300, 1000, 300);
            }
        }, 2L);
    }

    private Location locationFromJson(String json) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(json, type);
        return new Location(
                Bukkit.getWorld((String) data.get("world")),
                ((Number) data.get("x")).doubleValue(),
                ((Number) data.get("y")).doubleValue(),
                ((Number) data.get("z")).doubleValue(),
                ((Number) data.get("yaw")).floatValue(),
                ((Number) data.get("pitch")).floatValue()
        );
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
