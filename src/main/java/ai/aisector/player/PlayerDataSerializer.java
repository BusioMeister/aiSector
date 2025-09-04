package ai.aisector.player;

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


    public static String serialize(Player player,Location loc) {
        Map<String, Object> data = new HashMap<>();
        int heldSlot = player.getInventory().getHeldItemSlot();
        data.put("heldSlot", heldSlot);
        data.put("heldItem", ItemStackUtil.serializeItemStack(player.getInventory().getItem(heldSlot)));

        // Pozycja gracza
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        data.put("world", loc.getWorld().getName());

        // Stan gracza
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

        // Ekwipunek
        data.put("inventory", ItemStackUtil.serializeItemStackArray(player.getInventory().getContents()));
        data.put("enderChest", ItemStackUtil.serializeItemStackArray(player.getEnderChest().getContents()));





        // Efekty mikstur
        data.put("effects", serializePotionEffects(player.getActivePotionEffects()));

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
        Map<String,Object> data = JsonUtil.fromJson(JSON, Map.class);

        Location loc = new Location(
                Bukkit.getWorld((String) data.get("world")),
                ((Number) data.get("x")).doubleValue(),
                ((Number) data.get("y")).doubleValue(),
                ((Number) data.get("z")).doubleValue(),
                ((Number) data.get("yaw")).floatValue(),
                ((Number) data.get("pitch")).floatValue()
        );

        // Ustaw pozycję przed innymi operacjami
        player.teleport(loc);

        // Bezpieczne pobieranie booleani
        boolean isFlying = getBooleanSafe(data, "isFlying");
        boolean isGliding = getBooleanSafe(data, "isGliding");
        boolean isSprinting = getBooleanSafe(data, "isSprinting");
        boolean isSneaking = getBooleanSafe(data, "isSneaking");

// Latanie tylko jeśli gracz ma tryb, który na to pozwala
        if (isFlying) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        }

// Elytra
        if (isGliding) {
            player.setGliding(true);
        }

// Sprint i kucanie
        player.setSprinting(isSprinting);
        player.setSneaking(isSneaking);


        // Stan gracza
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

        // Ekwipunek
        player.getInventory().setContents(ItemStackUtil.deserializeItemStackArray((String) data.get("inventory")));
        player.getEnderChest().setContents(ItemStackUtil.deserializeItemStackArray((String) data.get("enderChest")));

        // Przedmiot w ręce — jeśli masz go osobno zapisany
        if (data.containsKey("heldSlot")) {
            int heldSlot = ((Number) data.get("heldSlot")).intValue();
            player.getInventory().setHeldItemSlot(heldSlot);
        }
        if (data.containsKey("heldItem")) {
            Object heldItemJson = data.get("heldItem");
            if (heldItemJson instanceof String) {
                player.getInventory().setItem(player.getInventory().getHeldItemSlot(),
                        ItemStackUtil.deserializeItemStack((String) heldItemJson));
            }
        }

        // Efekty mikstur
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
