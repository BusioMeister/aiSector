package ai.aisector;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerDataSerializer {


    public static String serialize(Player player,Location loc) {
        Map<String, Object> data = new HashMap<>();
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
        Map<String,Object>data = JsonUtil.fromJson(JSON, Map.class);
        Location loc = new Location(
                Bukkit.getWorld((String) data.get("world")),
                (double) data.get("x"),
                (double) data.get("y"),
                (double) data.get("z"),
                ((Number) data.get("yaw")).floatValue(),
                ((Number) data.get("pitch")).floatValue()
        );

        // Ustawienie pozycji
        player.teleport(loc);

        // Stan gracza
        player.setHealth((double) data.get("health"));
        player.setFoodLevel(((Number) data.get("hunger")).intValue());
        player.setSaturation(((Number) data.get("saturation")).floatValue());
        player.setAbsorptionAmount((double) data.get("absorption"));
        player.setRemainingAir(((Number) data.get("air")).intValue());
        player.setFireTicks(((Number) data.get("fireTicks")).intValue());
        player.setTotalExperience(((Number) data.get("experience")).intValue());
        player.setLevel(((Number) data.get("level")).intValue());
        player.setExp(((Number) data.get("exp")).floatValue());
        player.setGameMode(GameMode.valueOf((String) data.get("gameMode")));

        // Ekwipunek
        player.getInventory().setContents(ItemStackUtil.deserializeItemStackArray( (String) data.get("inventory")));
        player.getEnderChest().setContents(ItemStackUtil.deserializeItemStackArray( (String) data.get("enderChest")));

        // Efekty mikstur
        player.addPotionEffects(deserializePotionEffects((List<Map<String, Object>>) data.get("effects")));
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
