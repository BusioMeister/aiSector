package ai.aisector.cobblex;

import ai.aisector.SectorPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class CobbleXManager {

    private final SectorPlugin plugin;
    private final List<Loot> table = new ArrayList<>();
    private double totalPct = 0.0;
    private final Random rnd = new Random();
    private File file;
    private YamlConfiguration cfg;

    public static final class Loot {
        public final Material mat;
        public final double chancePct;
        public final int min;
        public final int max;
        public Loot(Material mat, double chancePct, int min, int max) {
            this.mat = mat; this.chancePct = chancePct; this.min = min; this.max = max;
        }
    }

    public CobbleXManager(SectorPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            // 1) Plik i wczytanie YAML
            this.file = new File(plugin.getDataFolder(), "cobblex.yml");
            if (!file.exists()) {
                plugin.saveResource("cobblex.yml", false);
            }
            this.cfg = YamlConfiguration.loadConfiguration(file);

            // 2) Czytamy sekcję „cobblex” lub cały root (oba formaty wspierane)
            ConfigurationSection root = cfg.getConfigurationSection("cobblex");
            if (root == null) root = cfg;

            // 3) Parsowanie
            this.table.clear();
            this.totalPct = 0.0;

            for (String k : root.getKeys(false)) {
                String matStr = root.getString(k + ".material", "AIR");
                double chance = root.getDouble(k + ".chance", 0.0);
                int min = root.getInt(k + ".min", 1);
                int max = root.getInt(k + ".max", 1);

                Material mat = Material.matchMaterial(matStr);
                if (mat == null || chance <= 0.0 || min <= 0 || max < min) {
                    continue;
                }
                this.table.add(new Loot(mat, chance, min, max));
                this.totalPct += chance;
            }

            plugin.getLogger().info("[CobbleX] Loaded " + table.size() + " loot entries (sum=" + totalPct + "%) from " + file.getName());
        } catch (Exception ex) {
            plugin.getLogger().severe("[CobbleX] Failed to load cobblex.yml: " + ex.getMessage());
            this.table.clear();
            this.totalPct = 0.0;
        }
    }

    public Optional<ItemStack> roll() {
        if (table.isEmpty()) return Optional.empty();
        double roll = rnd.nextDouble() * totalPct;
        double acc = 0.0;
        for (Loot l : table) {
            acc += l.chancePct;
            if (roll <= acc) {
                int amt = l.min + rnd.nextInt(l.max - l.min + 1);
                return Optional.of(new ItemStack(l.mat, amt));
            }
        }
        // fallback
        Loot last = table.get(table.size() - 1);
        int amt = last.min + rnd.nextInt(last.max - last.min + 1);
        return Optional.of(new ItemStack(last.mat, amt));
    }

    public List<Loot> getTable() {
        return Collections.unmodifiableList(table);
    }
}
