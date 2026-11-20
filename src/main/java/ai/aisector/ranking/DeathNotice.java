package ai.aisector.ranking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public record DeathNotice(String tag, String killer, Integer loss, int droppedPlaces) {
    static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(400));

    public String marker() {
        return "pvp:" + (loss == null ? "-" : loss) + ":" + tag + ":" + killer + ":" + droppedPlaces;
    }

    public static DeathNotice parse(String marker) {
        if (marker == null || marker.length() > 96) return null;
        String[] parts = marker.split(":", -1);
        if ((parts.length != 4 && parts.length != 5) || !parts[0].equals("pvp") || !parts[1].matches("-|[0-9]{1,2}")
                || !parts[2].matches("[\\p{L}\\p{N}_-]{0,32}") || !parts[3].matches("[A-Za-z0-9_]{1,16}")) return null;
        Integer loss = parts[1].equals("-") ? null : Integer.valueOf(parts[1]);
        if (loss != null && loss > 50) return null;
        if (parts.length == 5 && !parts[4].matches("[0-9]{1,9}")) return null;
        return new DeathNotice(parts[2], parts[3], loss, parts.length == 5 ? Integer.parseInt(parts[4]) : 0);
    }

    public static Component name(String tag, String nick, NamedTextColor color) {
        Component nickname = Component.text(nick, NamedTextColor.GRAY);
        return tag.isEmpty() ? nickname : Component.text("[" + tag + "] ", color).append(nickname);
    }

    public void show(Player victim) {
        victim.showTitle(Title.title(name(tag, killer, NamedTextColor.RED),
                Component.text(loss == null ? "Zabił Cię" : "−" + loss + " pkt"
                        + (droppedPlaces > 0 ? "  ↓" + droppedPlaces : ""), NamedTextColor.RED), TIMES));
    }
}
