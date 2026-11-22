package ai.aisector.ranking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CombatTitle {
    private static final Title.Times TIMES = Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400));
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> animations = new HashMap<>();

    public CombatTitle(Plugin plugin) { this.plugin = plugin; }

    private record Glyph(String text, NamedTextColor color) {}

    public void show(Player player, String tag, String nick, String suffix, NamedTextColor accent, boolean sound) {
        show(player, tag, nick, suffix, accent, sound, false);
    }

    public void show(Player player, String tag, String nick, String suffix, NamedTextColor accent, boolean sound, boolean guildmate) {
        show(player, "", tag, nick, suffix, accent, sound, guildmate);
    }

    public void show(Player player, String prefix, String tag, String nick, String suffix,
                     NamedTextColor accent, boolean sound, boolean guildmate) {
        UUID id = player.getUniqueId();
        cancel(id);
        var glyphs = new ArrayList<Glyph>();
        if (!prefix.isEmpty()) append(glyphs, prefix + " ", accent);
        if (!tag.isEmpty()) append(glyphs, "[" + tag + "] ", guildmate ? NamedTextColor.GREEN : accent);
        append(glyphs, nick, guildmate ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        if (!suffix.isEmpty()) append(glyphs, "  " + suffix, accent);
        var line = Component.text();
        player.sendTitlePart(TitlePart.TIMES, TIMES);
        player.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
        animations.put(id, new BukkitRunnable() {
            private int index;
            @Override public void run() {
                if (Bukkit.getPlayer(id) != player || !player.isOnline() || player.isDead()) {
                    CombatTitle.this.cancel(id);
                    return;
                }
                Glyph glyph = glyphs.get(index++);
                add(line, glyph);
                // Reveal intervening spaces in the same frame, so there are no silent pauses.
                while (index < glyphs.size() && glyphs.get(index).text().isBlank()) add(line, glyphs.get(index++));
                player.sendTitlePart(TitlePart.TITLE, line.build());
                if (sound) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                        SoundCategory.PLAYERS, .16f, 1.15f + .45f * index / glyphs.size());
                if (index >= glyphs.size()) CombatTitle.this.cancel(id);
            }
        }.runTaskTimer(plugin, 0L, 1L));
    }

    private static void append(List<Glyph> glyphs, String text, NamedTextColor color) {
        text.codePoints().forEach(cp -> glyphs.add(new Glyph(Character.toString(cp), color)));
    }

    private static void add(TextComponent.Builder line, Glyph glyph) {
        line.append(Component.text(glyph.text(), glyph.color()));
    }

    public void cancel(UUID id) {
        var task = animations.remove(id);
        if (task != null) task.cancel();
    }
}
