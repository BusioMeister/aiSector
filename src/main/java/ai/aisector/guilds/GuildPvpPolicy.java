package ai.aisector.guilds;

/** Pure fail-closed checks for inconsistent guild profile/cache state. */
final class GuildPvpPolicy {
    private GuildPvpPolicy() { }

    static boolean hasUnresolvedGuild(String guildTag, Guild guild) {
        return guildTag != null && !guildTag.isBlank() && guild == null;
    }
}
