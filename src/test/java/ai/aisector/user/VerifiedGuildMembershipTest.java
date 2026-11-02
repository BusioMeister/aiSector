package ai.aisector.user;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedGuildMembershipTest {
    @Test void derivesRoleFromCurrentGuildRatherThanRequestedOwnerRole() {
        UUID member = UUID.randomUUID();
        Document guild = new Document("tag", "G").append("owner", UUID.randomUUID().toString())
                .append("members", List.of(member.toString()));
        assertEquals("MEMBER", VerifiedGuildMembership.from(member, guild).role());
        guild.put("mods", List.of(member.toString()));
        assertEquals("MOD", VerifiedGuildMembership.from(member, guild).role());
        guild.put("owner", member.toString());
        assertEquals("OWNER", VerifiedGuildMembership.from(member, guild).role());
    }
    @Test void missingGuildOrRemovedMemberClearsAssignment() {
        UUID member = UUID.randomUUID();
        assertNull(VerifiedGuildMembership.from(member, null).tag());
        assertNull(VerifiedGuildMembership.from(member, new Document("tag", "G")).role());
    }
    @Test void reusedTagDoesNotCarryOwnerAuthorityFromThePreviousGuild() {
        UUID player = UUID.randomUUID();
        Document old = new Document("tag", "G").append("owner", player.toString()).append("members", List.of(player.toString()))
                .append("_generation", "old");
        Document current = new Document(old).append("owner", UUID.randomUUID().toString()).append("_generation", "new");
        assertNotEquals(VerifiedGuildMembership.from(player, old), VerifiedGuildMembership.from(player, current));
        var model = new ai.aisector.guilds.Guild("G", "new guild", UUID.randomUUID());
        model.getMembers().add(player);
        assertEquals("MEMBER", model.roleOf(player));
        assertNull(model.roleOf(UUID.randomUUID()));
    }
}
