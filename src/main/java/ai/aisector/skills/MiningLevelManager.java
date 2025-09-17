package ai.aisector.skills;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.Rank;
import ai.aisector.user.User;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MiningLevelManager {

    private final SectorPlugin plugin;
    private static final int MAX_LEVEL = 100;
    private static final int BASE_XP = 200;
    private static final double EXPONENT = 1.7;
    private static final Map<Material, Integer> XP_VALUES = new EnumMap<>(Material.class);
    private static final Map<String, Map<Material, Double>> RANK_BONUS_PCT = new HashMap<>();

    static {

        // Tutaj definiujemy, ile XP daje wykopanie jakiej rudy
        XP_VALUES.put(Material.DIAMOND, 15);
        XP_VALUES.put(Material.GOLD_INGOT, 7);
        XP_VALUES.put(Material.IRON_INGOT, 3);
        XP_VALUES.put(Material.LAPIS_LAZULI, 3); // Dodano z Twojego screena
        XP_VALUES.put(Material.REDSTONE, 4);     // Dodano z Twojego screena
        putRankBonus("GRACZ", 0.0, 0.0, 0.0, 0.0, 0.0);
        putRankBonus("VIP",   0.25, 0.25, 0.25, 0.25, 0.25);
        putRankBonus("SVIP",  0.50, 0.50, 0.50, 0.50, 0.50);
        putRankBonus("ELITE", 1.00, 1.00, 1.00, 1.00, 1.00);
    }
    private static void putRankBonus(String rankName,
                                     double diamond, double gold, double iron, double lapis, double redstone) {
        Map<Material, Double> m = new EnumMap<>(Material.class);
        m.put(Material.DIAMOND, diamond);
        m.put(Material.GOLD_INGOT, gold);
        m.put(Material.IRON_INGOT, iron);
        m.put(Material.LAPIS_LAZULI, lapis);
        m.put(Material.REDSTONE, redstone);
        RANK_BONUS_PCT.put(rankName.toUpperCase(), m);
    }
    public MiningLevelManager(SectorPlugin plugin) { this.plugin = plugin; }



    // --- DODANO BRAKUJĄCĄ METODĘ ---
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

        // --- POPRAWKA BŁĘDU long/int ---
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
    public double getRankBonusPercent(Rank rank, Material material) {
        if (rank == null) return 0.0;
        Map<Material, Double> m = RANK_BONUS_PCT.get(rank.getName().toUpperCase());
        if (m == null) return 0.0;
        return m.getOrDefault(material, 0.0);
    }

    public double getDropChanceBonus(int level) {
        return level * 0.0108;
    }
}