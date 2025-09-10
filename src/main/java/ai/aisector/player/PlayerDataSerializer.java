package ai.aisector.player;

import ai.aisector.commands.GodCommand;
import ai.aisector.utils.ItemStackUtil;
import ai.aisector.utils.JsonUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class PlayerDataSerializer {

    public static String serialize(Player player, Location loc) {
        Map<String, Object> data = new HashMap<>();

        // Twoje istniejące dane...
        data.put("heldSlot", player.getInventory().getHeldItemSlot());
        data.put("heldItem", ItemStackUtil.serializeItemStack(player.getInventory().getItem(player.getInventory().getHeldItemSlot())));
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        data.put("world", loc.getWorld().getName());
        data.put("health", player.getHealth());
        data.put("hunger", player.getFoodLevel());
        data.put("saturation", player.getSaturation());
        data.put("absorption", player.getAbsorptionAmount());
        data.put("air", player.getRemainingAir());
        data.put("fireTicks", player.getFireTicks());
        data.put("experience", player.getTotalExperience());
        data.put("level", player.getLevel());
        data.put("exp", player.getExp());
        data.put("gameMode", player.getGameMode().name());
        data.put("isFlying", player.isFlying());
        data.put("isGliding", player.isGliding());
        data.put("isSprinting", player.isSprinting());
        data.put("isSneaking", player.isSneaking()); // Warto dodać
        data.put("inventory", ItemStackUtil.serializeItemStackArray(player.getInventory().getContents()));
        data.put("enderChest", ItemStackUtil.serializeItemStackArray(player.getEnderChest().getContents()));
        data.put("effects", serializePotionEffects(player.getActivePotionEffects()));

        // 🔥 NOWE DANE DO SYNCHRONIZACJI
        data.put("allowFlight", player.getAllowFlight());
        data.put("flySpeed", player.getFlySpeed());
        data.put("walkSpeed", player.getWalkSpeed());
        data.put("isInvulnerable", player.isInvulnerable()); // God mode

        return JsonUtil.toJson(data);
    }
    public static List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        List<Map<String, Object>> serializedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            effectData.put("ambient", effect.isAmbient());
            effectData.put("particles", effect.hasParticles());
            effectData.put("icon", effect.hasIcon());
            serializedEffects.add(effectData);
        }
        return serializedEffects;
    }

    public static void deserialize(Player player, String JSON) {
        Map<String, Object> data = JsonUtil.fromJson(JSON, Map.class);

        Location loc = new Location(
                Bukkit.getWorld((String) data.get("world")),
                ((Number) data.get("x")).doubleValue(),
                ((Number) data.get("y")).doubleValue(),
                ((Number) data.get("z")).doubleValue(),
                ((Number) data.get("yaw")).floatValue(),
                ((Number) data.get("pitch")).floatValue()
        );

        player.teleport(loc);

        // 🔥 ZAKTUALIZOWANA I POPRAWIONA LOGIKA
        // Ustaw prędkości
        if (data.containsKey("walkSpeed")) {
            player.setWalkSpeed(((Number) data.get("walkSpeed")).floatValue());
        }
        if (data.containsKey("flySpeed")) {
            player.setFlySpeed(((Number) data.get("flySpeed")).floatValue());
        }

        // Ustaw latanie (działa teraz dla każdego trybu gry)
        boolean allowFlight = getBooleanSafe(data, "allowFlight");
        boolean isFlying = getBooleanSafe(data, "isFlying");
        player.setAllowFlight(allowFlight);
        if (allowFlight && isFlying) {
            player.setFlying(true);
        }

        // Ustaw God Mode
        boolean isInvulnerable = getBooleanSafe(data, "isInvulnerable");
        player.setInvulnerable(isInvulnerable);
        // Zaktualizuj listę w GodCommand, aby zachować spójność
        if (isInvulnerable) {
            GodCommand.gods.add(player.getUniqueId());
        } else {
            GodCommand.gods.remove(player.getUniqueId());
        }

        // Reszta Twoich istniejących danych...
        player.setGliding(getBooleanSafe(data, "isGliding"));
        player.setSprinting(getBooleanSafe(data, "isSprinting"));
        player.setSneaking(getBooleanSafe(data, "isSneaking"));

        player.setHealth(((Number) data.get("health")).doubleValue());
        player.setFoodLevel(((Number) data.get("hunger")).intValue());
        player.setSaturation(((Number) data.get("saturation")).floatValue());
        player.setAbsorptionAmount(((Number) data.get("absorption")).doubleValue());
        player.setRemainingAir(((Number) data.get("air")).intValue());
        player.setFireTicks(((Number) data.get("fireTicks")).intValue());
        player.setTotalExperience(((Number) data.get("experience")).intValue());
        player.setLevel(((Number) data.get("level")).intValue());
        player.setExp(((Number) data.get("exp")).floatValue());
        player.setGameMode(GameMode.valueOf((String) data.get("gameMode")));
        player.getInventory().setContents(ItemStackUtil.deserializeItemStackArray((String) data.get("inventory")));
        player.getEnderChest().setContents(ItemStackUtil.deserializeItemStackArray((String) data.get("enderChest")));

        if (data.containsKey("heldSlot")) {
            player.getInventory().setHeldItemSlot(((Number) data.get("heldSlot")).intValue());
        }

        player.addPotionEffects(deserializePotionEffects((List<Map<String, Object>>) data.get("effects")));
    }


    private static boolean getBooleanSafe(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return false;
    }


    public static Set<PotionEffect> deserializePotionEffects(List<Map<String, Object>> serializedEffects) {
        Set<PotionEffect> effects = new HashSet<>();
        for (Map<String, Object> effectData : serializedEffects) {
            PotionEffectType type = PotionEffectType.getByName((String) effectData.get("type"));
            if (type != null) {
                effects.add(new PotionEffect(
                        type,
                        ((Number) effectData.get("duration")).intValue(),
                        ((Number) effectData.get("amplifier")).intValue(),
                        (boolean) effectData.get("ambient"),
                        (boolean) effectData.get("particles"),
                        (boolean) effectData.get("icon")
                ));
            }
        }
        return effects;
    }


}
