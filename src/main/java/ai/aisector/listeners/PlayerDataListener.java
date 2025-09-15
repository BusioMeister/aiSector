package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.database.RedisManager;
import ai.aisector.sectors.Sector;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.user.User;
import ai.aisector.user.UserManager;
import ai.aisector.utils.HotbarSlotSync;
import ai.aisector.utils.MessageUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final UserManager userManager;
    private final SectorManager sectorManager;
    private final WorldBorderManager borderManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();


    public PlayerDataListener(SectorPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        this.sectorManager = plugin.getSectorManager();
        this.borderManager = plugin.getWorldBorderManager();
        this.redisManager = plugin.getRedisManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConfigure(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            User user = userManager.getUser(player);
            if (user == null) {
                player.kickPlayer("§cWystąpił krytyczny błąd podczas ładowania Twojego profilu.");
                return;
            }

            applyPersistentData(player, user);

            try (Jedis jedis = redisManager.getJedis()) {
                // --- POCZĄTEK ZMIANY ---
                // Sprawdzamy, czy gracz dołącza w wyniku respawnu
                String respawnKey = "player:is_respawning:" + player.getUniqueId();
                boolean isRespawning = jedis.exists(respawnKey);
                if (isRespawning) {
                    jedis.del(respawnKey); // Usuwamy sygnał
                }
                // --- KONIEC ZMIANY ---

                String playerDataKey = "player:data:" + player.getUniqueId();
                String playerData = jedis.get(playerDataKey);
                Integer heldSlotBox = null;
                if (playerData != null) {
                    jedis.del(playerDataKey);
                    heldSlotBox = loadTransferData(player, playerData);
                }

                String finalTargetKey = "player:final_teleport_target:" + player.getUniqueId();
                String finalTargetData = jedis.get(finalTargetKey);
                if (finalTargetData != null) {
                    jedis.del(finalTargetKey);
                    Location finalTargetLocation = locationFromJson(finalTargetData);
                    Integer finalHeld = heldSlotBox;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.teleport(finalTargetLocation);
                            if (finalHeld != null) new HotbarSlotSync(plugin).ensureSelectedSlot(player, finalHeld);

                            // Jeśli gracz się respawnuje, pokaż tytuł śmierci, w przeciwnym wypadku normalne powitanie
                            if (isRespawning) {
                                showDeathTitle(player);
                            } else {
                                sendWelcomePackage(player);
                            }
                        }
                    }, 2L);
                    return;
                }

                String forceSpawnKey = "player:force_sector_spawn:" + player.getUniqueId();
                String targetSectorName = jedis.get(forceSpawnKey);
                if (targetSectorName != null) {
                    jedis.del(forceSpawnKey);
                    Integer finalHeld = heldSlotBox;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            Sector targetSector = sectorManager.getSectorByName(targetSectorName);
                            if (targetSector != null) {
                                Location sectorSpawn = sectorManager.getSectorSpawnLocation(targetSector);
                                if (sectorSpawn != null) {
                                    player.teleport(sectorSpawn);
                                    if (finalHeld != null)
                                        new HotbarSlotSync(plugin).ensureSelectedSlot(player, finalHeld);
                                    sendWelcomePackage(player);
                                    player.sendMessage("§aZostałeś przeniesiony na sektor §e" + targetSector.getName() + "§a.");
                                }
                            }
                        }
                    }, 5L);
                    return;
                }

                if (heldSlotBox != null) {
                    new HotbarSlotSync(plugin).ensureSelectedSlot(player, heldSlotBox);
                }
                if (isRespawning) {
                    showDeathTitle(player);
                } else {
                    sendWelcomePackage(player);
                }
            }
        }, 1L);
    }
    private void applyPersistentData(Player player, User user) {
        player.setInvulnerable(user.isGodMode());
        player.setAllowFlight(user.isFlying());
        if (user.isFlying()) {
            player.setFlying(true);
        }
        player.setWalkSpeed(user.getWalkSpeed());
        player.setFlySpeed(user.getFlySpeed());

        if (user.isVanished()) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                    onlinePlayer.hidePlayer(plugin, player);
                }
            }
        }
    }

    private Integer loadTransferData(Player player, String playerData) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(playerData, type);
        Location loc = locationFromJson(playerData);
        double health = ((Number) data.get("health")).doubleValue();
        int hunger = ((Number) data.get("hunger")).intValue();
        GameMode gameMode = GameMode.valueOf((String) data.get("gameMode"));
        int heldSlot = data.containsKey("heldSlot") ? ((Number) data.get("heldSlot")).intValue() : 0;

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

        player.teleport(loc);

        final ItemStack[] finalInventoryContents = inventoryContents;
        final ItemStack[] finalArmorContents = armorContents;
        final ItemStack finalOffhandItem = offhandItem;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.setHealth(Math.max(0.1, Math.min(health, player.getMaxHealth())));
            player.setFoodLevel(hunger);
            player.setGameMode(gameMode);
            if (finalInventoryContents != null) player.getInventory().setContents(finalInventoryContents);
            if (finalArmorContents != null) player.getInventory().setArmorContents(finalArmorContents);
            if (finalOffhandItem != null) player.getInventory().setItemInOffHand(finalOffhandItem);
            for (PotionEffect currentEffect : player.getActivePotionEffects()) { player.removePotionEffect(currentEffect.getType()); }
            if (effects != null && !effects.isEmpty()) player.addPotionEffects(effects);
        });

        player.setInvulnerable(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                User user = plugin.getUserManager().getUser(player);
                if (user == null || !user.isGodMode()) player.setInvulnerable(false);
            }
        }, 100L);

        return heldSlot;
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
    private void showDeathTitle(Player player) {
        // Używamy Title.Times, aby tytuł był widoczny dłużej
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500),  // fadeIn
                java.time.Duration.ofMillis(1500), // stay
                java.time.Duration.ofMillis(500)  // fadeOut
        );
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                Component.text("UMARŁEŚ!", NamedTextColor.RED), // Czerwony, główny tytuł
                Component.text(""), // Pusty podtytuł
                times
        );
        player.showTitle(title);

        // Upewniamy się, że border jest poprawny
        Sector sector = sectorManager.getSector(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (sector != null) {
            borderManager.sendWorldBorder(player, sector);
        }
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