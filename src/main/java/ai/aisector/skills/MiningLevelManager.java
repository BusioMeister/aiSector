package ai.aisector.skills;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MiningLevelManager {

    private final SectorPlugin plugin;
    private final LuckPermsBridge luckPerms;
    private final AtomicBoolean luckPermsWarningLogged = new AtomicBoolean();

    private record LuckPermsBridge(Object playerAdapter, Method getMetaData, Method getUser,
                                   Method getMetaValue, Method getPrimaryGroup) { }

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
        this.luckPerms = loadLuckPermsBridge();
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
        String keySpecific = "dropbonus." + material.name().toLowerCase();
        String keyAll = "dropbonus.all";
        try {
            Object meta = luckPerms.getMetaData().invoke(luckPerms.playerAdapter(), player);
            String raw = (String) luckPerms.getMetaValue().invoke(meta, keySpecific);
            if (raw == null) raw = (String) luckPerms.getMetaValue().invoke(meta, keyAll);
            if (raw == null) return null;
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        } catch (ReflectiveOperationException e) {
            logLuckPermsFailure(e);
            return null;
        }
    }

    public String getPrimaryGroup(Player player) {
        if (luckPerms == null) return null;
        try {
            Object user = luckPerms.getUser().invoke(luckPerms.playerAdapter(), player);
            return (String) luckPerms.getPrimaryGroup().invoke(user);
        } catch (ReflectiveOperationException e) {
            logLuckPermsFailure(e);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LuckPermsBridge loadLuckPermsBridge() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("net.luckperms.api.LuckPerms");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration((Class) apiClass);
            if (provider == null) {
                plugin.getLogger().warning("LuckPerms plugin is present but its API service is unavailable; using configured fallback bonuses.");
                return null;
            }

            Class<?> adapterClass = Class.forName("net.luckperms.api.platform.PlayerAdapter");
            Class<?> metadataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Object adapter = apiClass.getMethod("getPlayerAdapter", Class.class)
                    .invoke(provider.getProvider(), Player.class);
            return new LuckPermsBridge(adapter,
                    adapterClass.getMethod("getMetaData", Object.class),
                    adapterClass.getMethod("getUser", Object.class),
                    metadataClass.getMethod("getMetaValue", String.class),
                    userClass.getMethod("getPrimaryGroup"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not initialize optional LuckPerms integration; using configured fallback bonuses: "
                    + exception.getMessage());
            return null;
        }
    }

    private void logLuckPermsFailure(Exception exception) {
        if (luckPermsWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("LuckPerms bonus lookup failed; using configured fallback bonuses: "
                    + exception.getMessage());
        }
    }
}
