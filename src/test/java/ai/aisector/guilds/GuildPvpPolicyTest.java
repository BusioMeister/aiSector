package ai.aisector.guilds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildPvpPolicyTest {
    @Test
    void onlyNonBlankGuildTagsWithoutResolvedRecordsAreFailClosed() {
        assertTrue(GuildPvpPolicy.hasUnresolvedGuild("TAG", null));
        assertFalse(GuildPvpPolicy.hasUnresolvedGuild(null, null));
        assertFalse(GuildPvpPolicy.hasUnresolvedGuild(" ", null));
        assertFalse(GuildPvpPolicy.hasUnresolvedGuild("TAG", new Guild("TAG")));
    }
}
