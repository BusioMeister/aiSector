package ai.aisector.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public class MessageUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Component main = LEGACY.deserialize(title);
        Component sub = LEGACY.deserialize(subtitle);

        Title msg = Title.title(main, sub,
                Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut)));

        player.showTitle(msg);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(LEGACY.deserialize(message));
    }

    public static void sendChat(Player player, String message) {
        player.sendMessage(LEGACY.deserialize(message));
    }
}
