package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.database.MongoDBManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GuildManager {

    private final MongoDBManager mongo;
    private final Map<String, Guild> guildsByTag = new ConcurrentHashMap<>();

    public GuildManager(SectorPlugin plugin) {
        this.mongo = plugin.getMongoDBManager();
        loadAllGuilds();
    }

    private MongoCollection<Document> col() {
        return mongo.getCollection("guilds");
    }

    // ===== ŁADOWANIE / CACHE =====

    private void loadAllGuilds() {
        for (Document doc : col().find()) {
            Guild guild = fromDocument(doc);
            if (guild != null) {
                guildsByTag.put(guild.getTag().toLowerCase(), guild);
            }
        }
    }

    public Guild getGuild(String tag) {
        if (tag == null) return null;
        return guildsByTag.get(tag.toLowerCase());
    }
    public Guild reloadGuild(String tag) {
        Document doc = col().find(Filters.eq("tag", tag)).first();
        if (doc == null) return null;
        Guild guild = fromDocument(doc);
        guildsByTag.put(tag.toLowerCase(), guild);
        return guild;
    }


    public Guild getGuildByMember(UUID uuid) {
        if (uuid == null) return null;
        for (Guild guild : guildsByTag.values()) {
            if (guild.getMembers().contains(uuid)) {
                return guild;
            }
        }
        return null;
    }

    public boolean exists(String tag) {
        return getGuild(tag) != null;
    }

    public Collection<Guild> getAllGuilds() {
        return Collections.unmodifiableCollection(guildsByTag.values());
    }

    // ===== CRUD GILDII =====

    public Guild createGuild(String tag, String name, UUID owner) {
        if (tag == null || name == null || owner == null) return null;
        if (exists(tag)) return null;

        // prosty konstruktor z domyślnymi setami w Guild
        Guild guild = new Guild(tag, name, owner);

        guild.getMembers().add(owner);

        guildsByTag.put(tag.toLowerCase(), guild);
        saveGuild(guild);
        return guild;
    }


    public void saveGuild(Guild guild) {
        if (guild == null) return;
        Document doc = toDocument(guild);
        col().replaceOne(
                Filters.eq("tag", guild.getTag()),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    public void deleteGuild(Guild guild) {
        if (guild == null) return;
        guildsByTag.remove(guild.getTag().toLowerCase());
        col().deleteOne(Filters.eq("tag", guild.getTag()));
    }

    // ===== KONWERSJA <-> DOCUMENT =====

    private Guild fromDocument(Document doc) {
        if (doc == null) return null;

        String tag = doc.getString("tag");
        String name = doc.getString("name");

        String ownerStr = doc.getString("owner");
        UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;

        Set<UUID> members = toUuidSet(doc.getList("members", String.class));
        Set<UUID> mods = toUuidSet(doc.getList("mods", String.class));
        Set<UUID> invitations = toUuidSet(doc.getList("invitations", String.class));

        Set<String> allied = new HashSet<>(
                doc.getList("alliedGuilds", String.class, Collections.emptyList())
        );
        Set<String> allyInv = new HashSet<>(
                doc.getList("allyInvitations", String.class, Collections.emptyList())
        );

        Guild guild = new Guild(tag, name, owner);

        guild.getMembers().addAll(members);
        guild.getMods().addAll(mods);
        guild.getInvitations().addAll(invitations);
        guild.getAlliedGuilds().addAll(allied);
        guild.getAllyInvitations().addAll(allyInv);
        guild.setHomeSector(doc.getString("homeSector"));
        guild.setHomeWorld(doc.getString("homeWorld"));
        guild.setHomeX(doc.getDouble("homeX") != null ? doc.getDouble("homeX") : 0.0);
        guild.setHomeY(doc.getDouble("homeY") != null ? doc.getDouble("homeY") : 0.0);
        guild.setHomeZ(doc.getDouble("homeZ") != null ? doc.getDouble("homeZ") : 0.0);
        Double yaw = doc.getDouble("homeYaw");
        Double pitch = doc.getDouble("homePitch");
        guild.setHomeYaw(yaw != null ? yaw.floatValue() : 0.0f);
        guild.setHomePitch(pitch != null ? pitch.floatValue() : 0.0f);


        return guild;
    }


    private Document toDocument(Guild guild) {
        return new Document("tag", guild.getTag())
                .append("name", guild.getName())
                .append("owner", guild.getOwner() != null ? guild.getOwner().toString() : null)
                .append("members", toStringList(guild.getMembers()))
                .append("mods", toStringList(guild.getMods()))
                .append("invitations", toStringList(guild.getInvitations()))
                .append("alliedGuilds", new ArrayList<>(guild.getAlliedGuilds()))
                .append("allyInvitations", new ArrayList<>(guild.getAllyInvitations()))
                // DOM GILDII:
                .append("homeSector", guild.getHomeSector())
                .append("homeWorld", guild.getHomeWorld())
                .append("homeX", guild.getHomeX())
                .append("homeY", guild.getHomeY())
                .append("homeZ", guild.getHomeZ())
                .append("homeYaw", guild.getHomeYaw())
                .append("homePitch", guild.getHomePitch());
    }


    private Set<UUID> toUuidSet(List<String> list) {
        if (list == null) return new HashSet<>();
        return list.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    private List<String> toStringList(Set<UUID> set) {
        return set.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
    }
}
