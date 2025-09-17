package ai.aisector.generators;

import ai.aisector.SectorPlugin;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeneratorManager {

    private final SectorPlugin plugin;
    private final Map<Location, GeneratorType> generators = new ConcurrentHashMap<>();

    public GeneratorManager(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    // Rejestracja generatora + natychmiastowy upsert do Mongo
    public void register(Location loc, GeneratorType type) {
        Location key = loc.toBlockLocation();
        generators.put(key, type);

        MongoCollection<Document> col = plugin.getMongoDBManager()
                .getCollection("generators")
                .withWriteConcern(WriteConcern.MAJORITY);

        Document filter = new Document("world", key.getWorld().getName())
                .append("x", key.getBlockX())
                .append("y", key.getBlockY())
                .append("z", key.getBlockZ());

        Document data = new Document(filter).append("type", type.name());
        col.updateOne(filter, new Document("$set", data), new UpdateOptions().upsert(true));
    }

    // Użyj tego tylko jeśli wprowadzisz mechanikę trwałego usuwania generatora
    public void unregister(Location loc) {
        Location key = loc.toBlockLocation();
        generators.remove(key);

        MongoCollection<Document> col = plugin.getMongoDBManager()
                .getCollection("generators")
                .withWriteConcern(WriteConcern.MAJORITY);

        Document filter = new Document("world", key.getWorld().getName())
                .append("x", key.getBlockX())
                .append("y", key.getBlockY())
                .append("z", key.getBlockZ());

        col.deleteOne(filter);
    }

    public boolean isGenerator(Location loc) {
        return generators.containsKey(loc.toBlockLocation());
    }

    public GeneratorType getType(Location loc) {
        return generators.get(loc.toBlockLocation());
    }

    // Odnowa bloku po czasie
    public void scheduleRegen(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        GeneratorType type = generators.get(loc);
        if (type == null) return;

        int delay = type.getRegenTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType().isAir()) {
                block.setType(type.getMaterial());
            }
        }, delay);
    }

    // Wczytanie generatorów po starcie (wywołaj po 1 ticku w onEnable)
    public void loadAll() {
        MongoCollection<Document> col = plugin.getMongoDBManager().getCollection("generators");
        long total = col.countDocuments();
        plugin.getLogger().info("[Generators] Found " + total + " documents in MongoDB before load.");

        int loaded = 0, skippedNoWorld = 0, skippedBad = 0;
        FindIterable<Document> docs = col.find();
        for (Document d : docs) {
            String worldName = d.getString("world");
            World w = Bukkit.getWorld(worldName);
            if (w == null) { skippedNoWorld++; continue; }

            Integer x = d.getInteger("x");
            Integer y = d.getInteger("y");
            Integer z = d.getInteger("z");
            String typeStr = d.getString("type");
            if (x == null || y == null || z == null || typeStr == null) { skippedBad++; continue; }

            try {
                GeneratorType type = GeneratorType.valueOf(typeStr);
                Location loc = new Location(w, x, y, z).toBlockLocation();
                generators.put(loc, type);
                loaded++;
            } catch (IllegalArgumentException ex) {
                skippedBad++;
            }
        }
        plugin.getLogger().info("[Generators] Loaded " + loaded + " (skipped no-world=" + skippedNoWorld + ", bad=" + skippedBad + ").");
    }

    // Snapshot na wyłączeniu – czyści kolekcję i wrzuca aktualny stan z pamięci
    public void saveAll() {
        MongoCollection<Document> col = plugin.getMongoDBManager()
                .getCollection("generators")
                .withWriteConcern(WriteConcern.MAJORITY);

        col.deleteMany(new Document());
        if (generators.isEmpty()) {
            plugin.getLogger().info("[Generators] Saved 0 generators to MongoDB.");
            return;
        }

        List<Document> batch = new ArrayList<>(generators.size());
        for (Map.Entry<Location, GeneratorType> e : generators.entrySet()) {
            Location l = e.getKey();
            batch.add(new Document("world", l.getWorld().getName())
                    .append("x", l.getBlockX())
                    .append("y", l.getBlockY())
                    .append("z", l.getBlockZ())
                    .append("type", e.getValue().name()));
        }
        col.insertMany(batch);

        long after = col.countDocuments();
        plugin.getLogger().info("[Generators] Saved " + batch.size() + " generators to MongoDB (count after=" + after + ").");
    }
}
