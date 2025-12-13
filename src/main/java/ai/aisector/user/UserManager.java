package ai.aisector.user;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.RedisManager;
import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import redis.clients.jedis.Jedis;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<UUID, User> onlineUsers = new ConcurrentHashMap<>();
    private final SectorPlugin plugin;
    private final MongoDBManager mongoDBManager;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    public UserManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.mongoDBManager = plugin.getMongoDBManager();
        this.redisManager = plugin.getRedisManager();
    }

    public void savePlayerDataForTransfer(Player player, Location targetLocation) {
        try (Jedis jedis = redisManager.getJedis()) {
            Map<String, Object> data = new HashMap<>();
            data.put("x", targetLocation.getX());
            data.put("y", targetLocation.getY());
            data.put("z", targetLocation.getZ());
            data.put("yaw", targetLocation.getYaw());
            data.put("pitch", targetLocation.getPitch());
            data.put("world", targetLocation.getWorld().getName());
            data.put("health", player.getHealth());
            data.put("hunger", player.getFoodLevel());
            data.put("saturation", player.getSaturation());
            data.put("experience", player.getTotalExperience());
            data.put("level", player.getLevel());
            data.put("exp", player.getExp());
            data.put("gameMode", player.getGameMode().name());
            data.put("inventory", itemStackArrayToBase64(player.getInventory().getContents()));
            data.put("armor", itemStackArrayToBase64(player.getInventory().getArmorContents()));
            data.put("offhand", itemStackToBase64(player.getInventory().getItemInOffHand()));
            data.put("effects", serializePotionEffects(player.getActivePotionEffects()));
            data.put("heldSlot", player.getInventory().getHeldItemSlot());

            String playerData = gson.toJson(data);
            jedis.setex("player:data:" + player.getUniqueId(), 60, playerData);
        }
    }
    public User loadOrGetUser(Player player) {
        User existing = onlineUsers.get(player.getUniqueId());
        if (existing != null) return existing;

        // jeśli z jakiegoś powodu nie został załadowany przez listener – ładujemy tu
        loadUser(player);
        return onlineUsers.get(player.getUniqueId());
    }
    // cache-only: nie dotyka Mongo
    public User getUser(UUID uuid) {
        if (uuid == null) return null;
        return (User) onlineUsers.get(uuid);
    }



    public void loadUser(Player player) {
        User user = new User(player.getUniqueId());

        Document userDoc = mongoDBManager.getCollection("users")
                .find(Filters.eq("uuid", player.getUniqueId().toString()))
                .first();

        if (userDoc != null) {
            user.setGodMode(userDoc.getBoolean("godMode", false));
            user.setFlying(userDoc.getBoolean("flying", false));
            user.setVanished(userDoc.getBoolean("vanished", false));
// ====== GILDIA ======
            user.setGuildTag(userDoc.getString("guildTag"));
            user.setGuildRole(userDoc.getString("guildRole"));

            if (userDoc.getDouble("walkSpeed") != null) {
                user.setWalkSpeed(userDoc.getDouble("walkSpeed").floatValue());
            } else {
                user.setWalkSpeed(0.2f);
            }

            if (userDoc.getDouble("flySpeed") != null) {
                user.setFlySpeed(userDoc.getDouble("flySpeed").floatValue());
            } else {
                user.setFlySpeed(0.1f);
            }

            user.setMiningLevel(userDoc.getInteger("miningLevel", 1));

            // ====== MINING EXPERIENCE ======
            Object expObject = userDoc.get("miningExperience");
            if (expObject instanceof Number) {
                user.setMiningExperience(((Number) expObject).longValue());
            } else {
                user.setMiningExperience(0L);
            }

            // ====== MINED BLOCKS ======
            Document minedBlocksDoc = userDoc.get("minedBlocks", Document.class);
            if (minedBlocksDoc != null) {
                for (String key : minedBlocksDoc.keySet()) {
                    try {
                        Material material = Material.valueOf(key);
                        int count = minedBlocksDoc.getInteger(key, 0);
                        user.getMinedBlocks().put(material, count);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // ====== DROP SETTINGS ======
            Document dropSettingsDoc = userDoc.get("dropSettings", Document.class);
            if (dropSettingsDoc != null) {
                for (String key : dropSettingsDoc.keySet()) {
                    try {
                        Material material = Material.valueOf(key);
                        user.setDropEnabled(material, dropSettingsDoc.getBoolean(key));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            user.setCobblestoneDropEnabled(userDoc.getBoolean("cobblestoneDropEnabled", true));

            // ====== NOWE POLA PVP / CZAS GRY ======
            user.setKills(userDoc.getInteger("kills", 0));
            user.setDeaths(userDoc.getInteger("deaths", 0));

            Object playTimeObj = userDoc.get("playTimeSeconds");
            if (playTimeObj instanceof Number) {
                user.setPlayTimeSeconds(((Number) playTimeObj).longValue());
            } else {
                user.setPlayTimeSeconds(0L);
            }
            // ======================================
        }

        // DOMYŚLNE HOMES – bez zmian, zostawiasz swój kod:
        FindIterable<Document> homeDocs = mongoDBManager.getCollection("player_homes")
                .find(Filters.eq("player_uuid", player.getUniqueId().toString()));

        for (Document doc : homeDocs) {
            Document locDoc = doc.get("location", Document.class);
            World world = Bukkit.getWorld(locDoc.getString("world"));
            if (world != null) {
                Location loc = new Location(
                        world,
                        locDoc.getDouble("x"),
                        locDoc.getDouble("y"),
                        locDoc.getDouble("z"),
                        locDoc.getDouble("yaw").floatValue(),
                        locDoc.getDouble("pitch").floatValue()
                );
                user.addHome(new Home(doc.getInteger("home_slot"), doc.getString("sector"), loc));
            }
        }

        onlineUsers.put(player.getUniqueId(), user);
    }

    public void unloadUser(Player player) {
        User user = onlineUsers.get(player.getUniqueId());
        if (user == null) return;

        // --- DROP SETTINGS ---
        Document dropSettingsDoc = new Document();
        for (Map.Entry<Material, Boolean> entry : user.getDropSettings().entrySet()) {
            dropSettingsDoc.append(entry.getKey().name(), entry.getValue());
        }

        // --- MINED BLOCKS ---
        Document minedBlocksDoc = new Document();
        for (Map.Entry<Material, Integer> entry : user.getMinedBlocks().entrySet()) {
            minedBlocksDoc.append(entry.getKey().name(), entry.getValue());
        }

        // --- ZAPIS DO MONGO ---
        mongoDBManager.getCollection("users").updateOne(
                Filters.eq("uuid", player.getUniqueId().toString()),
                Updates.combine(
                        Updates.set("name", player.getName()),
                        Updates.set("godMode", user.isGodMode()),
                        Updates.set("flying", user.isFlying()),
                        Updates.set("vanished", user.isVanished()),
                        Updates.set("walkSpeed", user.getWalkSpeed()),
                        Updates.set("flySpeed", user.getFlySpeed()),
                        Updates.set("dropSettings", dropSettingsDoc),
                        Updates.set("cobblestoneDropEnabled", user.isCobblestoneDropEnabled()),
                        Updates.set("miningLevel", user.getMiningLevel()),
                        Updates.set("minedBlocks", minedBlocksDoc),
                        Updates.set("miningExperience", user.getMiningExperience()),
                        // ====== NOWE POLA PVP / CZAS GRY ======
                        Updates.set("kills", user.getKills()),
                        Updates.set("deaths", user.getDeaths()),
                        Updates.set("playTimeSeconds", user.getPlayTimeSeconds()),
                        Updates.set("guildTag", user.getGuildTag()),
                        Updates.set("guildRole", user.getGuildRole())
                        // ======================================
                ),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
        );

        onlineUsers.remove(player.getUniqueId());
    }


    public User getUser(Player player) { return onlineUsers.get(player.getUniqueId()); }

    private List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        List<Map<String, Object>> serializedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            serializedEffects.add(effectData);
        }
        return serializedEffects;
    }

    private String itemStackToBase64(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { return null; }
    }

    private String itemStackArrayToBase64(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) { dataOutput.writeObject(item); }
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { return null; }
    }
}