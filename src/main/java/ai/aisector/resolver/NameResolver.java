package ai.aisector.resolver;

import ai.aisector.SectorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bson.Document;
import redis.clients.jedis.Jedis;

import java.util.Optional;
import java.util.UUID;

public class NameResolver {
    private final SectorPlugin plugin;
    public NameResolver(SectorPlugin plugin) { this.plugin = plugin; }

    public Optional<UUID> resolveUuid(String input) {
        // 1) UUID wprost
        try { return Optional.of(UUID.fromString(input)); } catch (Exception ignored) {}

        String nameLower = input.toLowerCase();

        // 2) Redis hash: global:players (name_lower -> uuid)
        try (Jedis j = plugin.getRedisManager().getJedis()) {
            String uuidStr = j.hget("global:players", nameLower);
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try { return Optional.of(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 3) Lokalnie online
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(input);
        if (online != null) return Optional.of(online.getUniqueId());

        // 4) Mongo "users": szukamy po name lub uuid (jeśli przechowujecie)
        try {
            Document doc = plugin.getMongoDBManager().getCollection("users")
                    .find(new Document("name", input)).first();
            if (doc != null && doc.getString("uuid") != null) {
                return Optional.of(UUID.fromString(doc.getString("uuid")));
            }
        } catch (Exception ignored) {}

        // 5) Bukkit offline (ostatnia deska ratunku)
        OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        return Optional.ofNullable(off != null ? off.getUniqueId() : null);
    }

    public Optional<String> resolveLastIp(UUID uuid) {
        try {
            Document doc = plugin.getMongoDBManager().getCollection("users")
                    .find(new Document("uuid", uuid.toString())).first();
            if (doc != null && doc.getString("last_ip") != null) {
                return Optional.of(doc.getString("last_ip"));
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }
}
