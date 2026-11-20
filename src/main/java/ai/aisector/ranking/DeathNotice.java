package ai.aisector.ranking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public record DeathNotice(String tag, String killer, Integer loss) {
    public String marker() {
        return "pvp:" + (loss == null ? "-" : loss) + ":" + tag + ":" + killer;
    }

    public static DeathNotice parse(String marker) {
        if (marker == null || marker.length() > 96) return null;
        String[] parts = marker.split(":", -1);
        if (parts.length != 4 || !parts[0].equals("pvp") || !parts[1].matches("-|[0-9]{1,2}")
                || !parts[2].matches("[\\p{L}\\p{N}_-]{0,32}") || !parts[3].matches("[A-Za-z0-9_]{1,16}")) return null;
        Integer loss = parts[1].equals("-") ? null : Integer.valueOf(parts[1]);
        return loss != null && loss > 50 ? null : new DeathNotice(parts[2], parts[3], loss);
    }

    public static Component name(String tag, String nick, NamedTextColor color) {
        Component nickname = Component.text(nick, color);
        return tag.isEmpty() ? nickname : Component.text("[" + tag + "] ", NamedTextColor.GOLD).append(nickname);
    }

    public void show(Player victim) {
        victim.showTitle(Title.title(name(tag, killer, NamedTextColor.RED),
                Component.text(loss == null ? "Zabił Cię" : "Zabił Cię  •  −" + loss + " pkt", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(350))));
    }
}
