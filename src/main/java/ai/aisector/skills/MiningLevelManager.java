package ai.aisector.skills;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MiningLevelManager {

    private final SectorPlugin plugin;
    private final LuckPerms luckPerms;

    private static final int MAX_LEVEL = 100;
    private static final int BASE_XP = 200;
    private static final double EXPONENT = 1.7;

    private static final Map<Material, Integer> XP_VALUES = new EnumMap<>(Material.class);

    // Fallback dla primary group (LP) gdy brak meta dropbonus.*
    private static final Map<String, Map<Material, Double>> GROUP_BONUS_PCT = new HashMap<>();

    static {
        // XP za pozyskanie materiału (dostosuj pod swój pipeline)
        XP_VALUES.put(Material.DIAMOND, 15);
        XP_VALUES.put(Material.GOLD_INGOT, 7);
        XP_VALUES.put(Material.IRON_INGOT, 3);
        XP_VALUES.put(Material.LAPIS_LAZULI, 3);
        XP_VALUES.put(Material.REDSTONE, 4);

        // Fallback według nazwy primary group w LP (UPPERCASE)
        putGroupBonus("GRACZ", 0.0, 0.0, 0.0, 0.0, 0.0);
        putGroupBonus("VIP",   0.25, 0.25, 0.25, 0.25, 0.25);
        putGroupBonus("SVIP",  0.50, 0.50, 0.50, 0.50, 0.50);
        putGroupBonus("ELITE", 1.00, 1.00, 1.00, 1.00, 1.00);
    }

    private static void putGroupBonus(String groupName,
                                      double diamond, double gold, double iron, double lapis, double redstone) {
        Map<Material, Double> m = new EnumMap<>(Material.class);
        m.put(Material.DIAMOND, diamond);
        m.put(Material.GOLD_INGOT, gold);
        m.put(Material.IRON_INGOT, iron);
        m.put(Material.LAPIS_LAZULI, lapis);
        m.put(Material.REDSTONE, redstone);
        GROUP_BONUS_PCT.put(groupName.toUpperCase(), m);
    }

    public MiningLevelManager(SectorPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = (provider != null) ? provider.getProvider() : null;
    }

    // XP kalkulacje
    public int getExperienceFor(Material material) {
        return XP_VALUES.getOrDefault(material, 0);
    }

    public long getExperienceForNextLevel(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return Long.MAX_VALUE;
        return (long) (BASE_XP * Math.pow(currentLevel, EXPONENT));
    }

    public void addExperience(Player player, Material material, int amount) {
        User user = plugin.getUserManager().getUser(player);
        if (user == null || user.getMiningLevel() >= MAX_LEVEL) return;

        int xpPerItem = getExperienceFor(material);
        if (xpPerItem == 0) return;

        long totalXpGained = (long) xpPerItem * amount;
        user.addMiningExperience(totalXpGained);
        user.incrementMinedBlockCount(material, amount);

        while (user.getMiningExperience() >= getExperienceForNextLevel(user.getMiningLevel())) {
            if (user.getMiningLevel() >= MAX_LEVEL) break;

            long requiredXp = getExperienceForNextLevel(user.getMiningLevel());
            user.setMiningExperience(user.getMiningExperience() - requiredXp);
            user.setMiningLevel(user.getMiningLevel() + 1);

            player.sendMessage("§a§lAWANS! §7Osiągnąłeś §e" + user.getMiningLevel() + " §7poziom kopania!");
        }
    }

    // NOWA wersja pod LuckPerms: bonus dropu z meta lub fallback z primary group
    public double getRankBonusPercent(Player player, Material material) {
        // 1) Meta: dropbonus.<material> lub dropbonus.all (wartość np. 0.25 dla 25%)
        Double metaVal = readMetaDropBonus(player, material);
        if (metaVal != null) {
            return metaVal;
        }

        // 2) Fallback: primary group -> mapa GROUP_BONUS_PCT
        String primary = getPrimaryGroup(player);
        if (primary != null) {
            Map<Material, Double> map = GROUP_BONUS_PCT.get(primary.toUpperCase());
            if (map != null) {
                return map.getOrDefault(material, 0.0);
            }
        }
        return 0.0;
    }

    // Zachowane dla zgodności — po migracji usuń i wyczyść import Rank
    @Deprecated
    public double getRankBonusPercent(Object deprecatedRank, Material material) {
        return 0.0;
    }

    public double getDropChanceBonus(int level) {
        return level * 0.0108;
    }

    // Helpers

    private Double readMetaDropBonus(Player player, Material material) {
        if (luckPerms == null) return null;
        CachedMetaData meta = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);

        String keySpecific = "dropbonus." + material.name().toLowerCase();
        String keyAll = "dropbonus.all";

        String raw = meta.getMetaValue(keySpecific);
        if (raw == null) raw = meta.getMetaValue(keyAll);
        if (raw == null) return null;

        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String getPrimaryGroup(Player player) {
        if (luckPerms == null) return null;
        return luckPerms.getPlayerAdapter(Player.class).getUser(player).getPrimaryGroup();
    }
}
