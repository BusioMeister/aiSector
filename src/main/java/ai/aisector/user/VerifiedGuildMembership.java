package ai.aisector.user;

import org.bson.Document;
import java.util.List;
import java.util.UUID;

/** Derives permissions-bearing roles from the authoritative guild, never an old command's requested role. */
public record VerifiedGuildMembership(String tag, String role, String generation) {
    public static VerifiedGuildMembership from(UUID player, Document guild) {
        if (guild == null || !guild.getList("members", String.class, List.of()).contains(player.toString()))
            return new VerifiedGuildMembership(null, null, null);
        String role = player.toString().equals(guild.getString("owner")) ? "OWNER"
                : guild.getList("mods", String.class, List.of()).contains(player.toString()) ? "MOD" : "MEMBER";
        String generation = guild.getString("_generation");
        if (generation == null || generation.isBlank()) {
            Object identity = guild.get("_id");
            generation = identity instanceof org.bson.types.ObjectId id ? "legacy:" + id.toHexString()
                    : "legacy:" + guild.getString("tag").trim().toUpperCase(java.util.Locale.ROOT);
        }
        return new VerifiedGuildMembership(guild.getString("tag"), role, generation);
    }
}
