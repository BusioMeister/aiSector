package ai.aisector.ranking;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public record DeathNotice(String tag, String killer, Integer loss, int droppedPlaces, boolean guildmate) {
    public DeathNotice(String tag, String killer, Integer loss, int droppedPlaces) {
        this(tag, killer, loss, droppedPlaces, false);
    }

    public String marker() {
        return "pvp:" + (loss == null ? "-" : loss) + ":" + tag + ":" + killer + ":" + droppedPlaces + (guildmate ? ":guild" : "");
    }

    public static DeathNotice parse(String marker) {
        if (marker == null || marker.length() > 96) return null;
        String[] parts = marker.split(":", -1);
        if ((parts.length < 4 || parts.length > 6) || !parts[0].equals("pvp") || !parts[1].matches("-|[0-9]{1,2}")
                || !parts[2].matches("[\\p{L}\\p{N}_-]{0,32}") || !parts[3].matches("[A-Za-z0-9_]{1,16}")) return null;
        Integer loss = parts[1].equals("-") ? null : Integer.valueOf(parts[1]);
        if (loss != null && loss > 50) return null;
        if (parts.length >= 5 && !parts[4].matches("[0-9]{1,9}")) return null;
        if (parts.length == 6 && !parts[5].equals("guild")) return null;
        return new DeathNotice(parts[2], parts[3], loss, parts.length >= 5 ? Integer.parseInt(parts[4]) : 0, parts.length == 6);
    }

    public void show(Player victim, CombatTitle animator) {
        String suffix = ai.aisector.utils.Messages.text("death-notice.killed-you");
        if (loss != null) suffix += " • " + ai.aisector.utils.Messages.text("death-notice.lost-points", loss)
                + (droppedPlaces > 0 ? "  ↓" + droppedPlaces : "");
        animator.show(victim, tag, killer, suffix, NamedTextColor.RED, false, guildmate);
    }
}
