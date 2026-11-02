package ai.aisector.guilds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Uses the same tag contract as the persistent guild writer, including legacy longer tags. */
public record GuildDeletionNotice(String tag, String generation) {
    public static GuildDeletionNotice parse(String message) {
        if (message == null || message.length() > 4096) throw new IllegalArgumentException("Invalid invalidation size");
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        if (!json.has("tag") || !json.get("tag").isJsonPrimitive()
                || !json.getAsJsonPrimitive("tag").isString()) throw new IllegalArgumentException("Invalid tag type");
        String tag = json.get("tag").getAsString();
        if (!GuildManager.isValidPersistentTag(tag)) throw new IllegalArgumentException("Invalid tag");
        String generation = null;
        if (json.has("generation")) {
            if (!json.get("generation").isJsonPrimitive() || !json.getAsJsonPrimitive("generation").isString())
                throw new IllegalArgumentException("Invalid generation type");
            generation = json.get("generation").getAsString();
            if (generation.isBlank() || generation.length() > 128) throw new IllegalArgumentException("Invalid generation");
        }
        return new GuildDeletionNotice(GuildManager.normalizeTag(tag), generation);
    }
}
