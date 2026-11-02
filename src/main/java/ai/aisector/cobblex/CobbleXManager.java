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

public class CobbleXManager {

    private final SectorPlugin plugin;
    private record LootTable(List<Loot> entries, double totalChance) { }
    private volatile LootTable lootTable = new LootTable(List.of(), 0.0);
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

            // Parse into a candidate and publish only after the file was read successfully.
            List<Loot> candidate = new ArrayList<>();
            double candidateTotal = 0.0;

            for (String k : root.getKeys(false)) {
                try {
                    CobbleXLootDefinition definition = CobbleXLootDefinition.parse(
                            root.get(k + ".material"), root.get(k + ".chance"),
                            root.get(k + ".min"), root.get(k + ".max"));
                    double nextTotal = candidateTotal + definition.chancePct();
                    if (!Double.isFinite(nextTotal)) {
                        throw new IllegalArgumentException("total chance overflow");
                    }
                    candidate.add(new Loot(definition.material(), definition.chancePct(),
                            definition.min(), definition.max()));
                    candidateTotal = nextTotal;
                } catch (IllegalArgumentException invalidEntry) {
                    plugin.getLogger().warning("[CobbleX] Skipping invalid loot entry '" + k + "': "
                            + invalidEntry.getMessage());
                    continue;
                }
            }

            this.lootTable = new LootTable(List.copyOf(candidate), candidateTotal);

            plugin.getLogger().info("[CobbleX] Loaded " + candidate.size() + " loot entries (sum="
                    + candidateTotal + "%) from " + file.getName());
        } catch (Exception ex) {
            plugin.getLogger().severe("[CobbleX] Failed to load cobblex.yml: " + ex.getMessage());
        }
    }

    public Optional<ItemStack> roll() {
        LootTable snapshot = lootTable;
        List<Loot> currentTable = snapshot.entries();
        double currentTotal = snapshot.totalChance();
        if (currentTable.isEmpty() || !Double.isFinite(currentTotal) || currentTotal <= 0.0) {
            return Optional.empty();
        }
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(currentTotal);
        double acc = 0.0;
        for (Loot l : currentTable) {
            acc += l.chancePct;
            if (roll < acc) {
                int amt = java.util.concurrent.ThreadLocalRandom.current().nextInt(l.min, l.max + 1);
                return Optional.of(new ItemStack(l.mat, amt));
            }
        }
        // fallback
        Loot last = currentTable.get(currentTable.size() - 1);
        int amt = java.util.concurrent.ThreadLocalRandom.current().nextInt(last.min, last.max + 1);
        return Optional.of(new ItemStack(last.mat, amt));
    }

    public List<Loot> getTable() {
        return lootTable.entries();
    }
}
