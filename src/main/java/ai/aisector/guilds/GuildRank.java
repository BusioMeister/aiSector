package ai.aisector.guilds;

import java.util.Set;

record GuildRank(String id, String name, Set<Right> rights) {
    enum Right {
        PLACE("Stawianie bloków"), BREAK("Niszczenie bloków"), INVITE("Zapraszanie graczy"),
        KICK("Wyrzucanie graczy"), SET_HOME("Ustawianie domu gildii"), PVP("Ustawianie PvP gildii"),
        ANNOUNCE("Alerty i wiadomości gildii"), ALLIANCE("Zarządzanie sojuszami");

        final String label;
        Right(String label) { this.label = label; }
    }

    static final GuildRank LEADER = new GuildRank("leader", "Lider", Set.of(Right.values()));
    static final GuildRank DEPUTY = new GuildRank("deputy", "Vlider", LEADER.rights());
    static final GuildRank MASTER = new GuildRank("master", "Mistrz", Set.of(Right.PLACE, Right.BREAK, Right.INVITE));
    static final GuildRank RECRUIT = new GuildRank("recruit", "Rekrut", Set.of(Right.PLACE, Right.BREAK));

    GuildRank {
        rights = Set.copyOf(rights);
    }

    GuildRank toggle(Right right) {
        var result = java.util.EnumSet.noneOf(Right.class);
        result.addAll(rights);
        if (!result.remove(right)) result.add(right);
        return new GuildRank(id, name, result);
    }
}
