package ai.aisector.tab;

import ai.aisector.SectorPlugin;
import ai.aisector.skills.MiningLevelManager;
import com.mongodb.client.model.Projections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkTabList implements Listener {
    private static final int COLUMN_WIDTH = 128;
    private static final String ASCII_ADVANCES = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
    private static final String LAYOUT_CHANNEL = "aisector:tab_panel";
    private static final byte[] HIDE_LISTED_ROWS = {1};
    private static final Component EMPTY = Component.empty();
    private static final Component BRAND = MiniMessage.miniMessage()
            .deserialize("<gradient:#ff3838:#ffca55:#ff3838><bold>✦ LASTPLAY.NET ✦</bold></gradient>");
    private static final Component GRID_FOOTER = Component.text("LastPlay.net  •  Ranking według zabójstw",
            NamedTextColor.GRAY);
    private static final TextColor GOLD = TextColor.color(0xffca55);
    private record Snapshot(NetworkRanking ranking, Component footer) {}
    private record Sent(Component header, Component footer) {}
    private record Stats(String rank, String tag, String role, int members, Integer playerRank, Integer guildRank,
                         Integer kills, Integer deaths) {}

    private final SectorPlugin plugin;
    private final MiningLevelManager ranks;
    private final Component buildLine;
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    public NetworkTabList(SectorPlugin plugin, MiningLevelManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
        this.buildLine = Component.text("aiSector " + plugin.getPluginMeta().getVersion() + "  •  Build ",
                NamedTextColor.GRAY).append(Component.text(readBuildId(plugin), GOLD));
    }

    private static String readBuildId(SectorPlugin plugin) {
        try (var stream = plugin.getResource("aisector-build.properties")) {
            var properties = new java.util.Properties();
            if (stream != null) properties.load(stream);
            String id = properties.getProperty("id", "");
            if (id.matches("[0-9A-F]{12}")) return id;
        } catch (IOException error) {
            plugin.getLogger().warning("Could not read build identifier: " + error.getMessage());
        }
        return "nieznany";
    }

    public void start() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, LAYOUT_CHANNEL);
        long offset = Math.floorMod(plugin.getConfig().getString("this-sector-name", "").hashCode(), 200);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 10 + offset, 600L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updatePlayers, 20L, 40L);
    }

    private void refresh() {
        if (Bukkit.getOnlinePlayers().isEmpty() || !refreshing.compareAndSet(false, true)) return;
        var mongo = plugin.getMongoDBManager();
        mongo.executeAsync(() -> {
            try (var users = mongo.getCollection("users").find().projection(
                    Projections.include("uuid", "name", "kills", "deaths")).maxTime(5, TimeUnit.SECONDS).cursor();
                 var guilds = mongo.getCollection("guilds").find().projection(
                    Projections.include("tag", "members")).maxTime(5, TimeUnit.SECONDS).cursor()) {
                var ranking = NetworkRanking.from(() -> users, () -> guilds);
                return new Snapshot(ranking, renderFooter(ranking));
            }
        }).whenComplete((value, error) -> {
            if (error == null) snapshot = value;
            else plugin.getLogger().warning("Could not refresh TAB rankings: " + error.getMessage());
            refreshing.set(false);
        });
    }

    private void updatePlayers() {
        if (snapshot == null) refresh();
        Snapshot current = snapshot;
        Component footer = current == null ? Component.text("Wczytywanie rankingu…", NamedTextColor.GRAY) : current.footer();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getUserManager().isTransferPending(player)) continue;
            NetworkRanking ranking = current == null ? null : current.ranking();
            boolean fixed = player.getListeningPluginChannels().contains(LAYOUT_CHANNEL);
            Sent next = fixed ? new Sent(renderPanel(slots(player, ranking), buildLine), Component.empty())
                    : new Sent(header(player, ranking), footer.append(Component.newline()).append(buildLine));
            if (!next.equals(sent.get(player.getUniqueId()))) {
                player.sendPlayerListHeaderAndFooter(next.header(), next.footer());
                sent.put(player.getUniqueId(), next);
            }
            if (fixed) player.sendPluginMessage(plugin, LAYOUT_CHANNEL, HIDE_LISTED_ROWS);
        }
    }

    private Stats stats(Player player, NetworkRanking ranking) {
        var user = plugin.getUserManager().getUser(player);
        String primaryGroup = ranks.getPrimaryGroup(player);
        String rank = primaryGroup == null || primaryGroup.isBlank() || primaryGroup.equalsIgnoreCase("default")
                ? (player.isOp() ? "Operator" : "Gracz") : primaryGroup;
        UUID id = player.getUniqueId();
        var guild = user != null && user.hasGuild() ? plugin.getGuildManager().getGuildCached(user.getGuildTag()) : null;
        String role = guild == null ? null : guild.roleOf(id);
        String tag = role == null ? null : guild.getTag().toUpperCase(Locale.ROOT);
        String guildRole = role == null ? "—" : id.equals(guild.getDeputy()) && !"OWNER".equals(role)
                ? "Zastępca" : switch (role) {
            case "OWNER" -> "Lider";
            case "MOD" -> "Moderator";
            default -> "Członek";
        };
        return new Stats(rank, tag, guildRole, role == null ? 0 : guild.getMembers().size(),
                ranking == null ? null : ranking.playerPositions().get(id.toString()),
                ranking == null || tag == null ? null : ranking.guildPositions().get(tag),
                user == null ? null : user.getKills(), user == null ? null : user.getDeaths());
    }

    private Component header(Player player, NetworkRanking ranking) {
        Stats stats = stats(player, ranking);
        return BRAND.append(Component.newline())
                .append(field("Nick: ", player.getName())).append(separator()).append(field("Ranga: ", stats.rank()))
                .append(Component.newline()).append(field("Zabójstwa: ", value(stats.kills()))).append(separator())
                .append(field("Śmierci: ", value(stats.deaths()))).append(separator())
                .append(field("Ranking: ", position(stats.playerRank())))
                .append(Component.newline()).append(field("Gildia: ", value(stats.tag())))
                .append(separator()).append(field("Rola: ", stats.role())).append(separator())
                .append(field("Członkowie: ", stats.members()))
                .append(separator()).append(field("Ranking gildii: ", position(stats.guildRank())))
                .append(Component.newline());
    }

    private List<Component> slots(Player player, NetworkRanking ranking) {
        Stats stats = stats(player, ranking);
        var result = new ArrayList<>(java.util.Collections.nCopies(80, EMPTY));
        result.set(2, field("Nick: ", player.getName()));
        result.set(3, icon("★", GOLD, field("Ranga: ", clip(stats.rank()))));
        result.set(4, icon("⚔", TextColor.color(0xff5555), field("Zabójstwa: ", value(stats.kills()))));
        result.set(5, icon("☠", NamedTextColor.WHITE, field("Śmierci: ", value(stats.deaths()))));
        result.set(6, icon("▲", GOLD, field("Ranking: ", position(stats.playerRank()))));
        result.set(8, title("TWÓJ SEKTOR"));
        result.set(10, icon("▲", GOLD, field("Sektor: ", plugin.getConfig().getString("this-sector-name", "—"))));
        result.set(11, field("Gracze na sektorze: ", Bukkit.getOnlinePlayers().stream().filter(player::canSee).count()));
        result.set(62, icon("⚑", GOLD, field("Gildia: ", stats.tag() == null ? "Brak" : clip(stats.tag()))));
        result.set(63, field("Rola: ", stats.role()));
        result.set(64, field("Członkowie: ", stats.members()));
        result.set(65, icon("▲", GOLD, field("Ranking gildii: ", position(stats.guildRank()))));
        result.set(68, title("LASTPLAY.NET"));
        result.set(70, Component.text("/g info", NamedTextColor.GRAY));
        result.set(71, Component.text("/spawn", NamedTextColor.GRAY));
        for (int i = 0; i < 20; i++) {
            result.set(20 + i, rankingRow(ranking == null ? List.of() : ranking.players(), i));
            result.set(40 + i, rankingRow(ranking == null ? List.of() : ranking.guilds(), i));
        }
        return result;
    }

    static Component renderPanel(List<Component> slots, Component buildLine) {
        var panel = Component.text().append(BRAND).append(Component.newline());
        appendPanelRow(panel, List.of(title("GRACZ I SEKTOR"), title("TOP 20 GRACZY"),
                title("TOP 20 GILDII"), title("TWOJA GILDIA")));
        for (int row = 0; row < 20; row++) {
            panel.append(Component.newline());
            appendPanelRow(panel, List.of(slots.get(row), slots.get(20 + row), slots.get(40 + row), slots.get(60 + row)));
        }
        return panel.append(Component.newline()).append(GRID_FOOTER)
                .append(Component.newline()).append(buildLine).build();
    }

    private static void appendPanelRow(net.kyori.adventure.text.TextComponent.Builder panel, List<Component> columns) {
        var plain = PlainTextComponentSerializer.plainText();
        for (Component column : columns) {
            int remaining = COLUMN_WIDTH - panelTextWidth(plain.serialize(column));
            panel.append(Component.text("│ ", NamedTextColor.DARK_GRAY)).append(column);
            // In the vanilla font a normal space advances 4 px and a bold space 5 px.
            // Use the latter for the remainder, instead of rounding each column to 4 px.
            int fineSpaces = Math.floorMod(remaining, 4);
            int normalSpaces = (remaining - 5 * fineSpaces) / 4;
            if (normalSpaces < 0) {
                panel.append(Component.space());
            } else {
                if (normalSpaces > 0) panel.append(Component.text(" ".repeat(normalSpaces)));
                if (fineSpaces > 0) panel.append(Component.text(" ".repeat(fineSpaces)).decorate(TextDecoration.BOLD));
            }
        }
        panel.append(Component.text("│", NamedTextColor.DARK_GRAY));
    }

    private static int panelTextWidth(String text) {
        return text.codePoints().map(c -> {
            if (c >= 32 && c <= 126) return ASCII_ADVANCES.charAt(c - 32) - '0';
            switch (c) {
                case '—': return 9;
                case '…', '★', '⚔', '☠', '⚑': return 8;
                case '▲', '│': return 6;
                case 'ł': return 5;
                case 'Ł': return 7;
                default: break;
            }
            String unaccented = java.text.Normalizer.normalize(Character.toString(c), java.text.Normalizer.Form.NFD);
            char base = unaccented.charAt(0);
            if (base >= 'A' && base <= 'z') return ASCII_ADVANCES.charAt(base - 32) - '0';
            return 6;
        }).sum();
    }

    private static Component rankingRow(List<NetworkRanking.Entry> entries, int index) {
        var row = Component.text("#" + (index + 1) + "  ", index == 0 ? GOLD : NamedTextColor.GRAY);
        if (index >= entries.size()) return row.append(Component.text("—", NamedTextColor.DARK_GRAY));
        var entry = entries.get(index);
        String kills = entry.kills() < 1000 ? Long.toString(entry.kills())
                : entry.kills() < 1_000_000 ? entry.kills() / 1_000 + "k"
                : entry.kills() < 1_000_000_000 ? entry.kills() / 1_000_000 + "M" : entry.kills() / 1_000_000_000 + "G";
        String label = fit(entry.label(), COLUMN_WIDTH - 14
                - panelTextWidth("#" + (index + 1) + "    " + kills));
        return row.append(Component.text(label, NamedTextColor.WHITE))
                .append(Component.text("  " + kills, GOLD));
    }

    private static String fit(String text, int width) {
        if (panelTextWidth(text) <= width) return text;
        int end = text.length();
        do { end = text.offsetByCodePoints(end, -1); }
        while (end > 0 && panelTextWidth(text.substring(0, end) + "…") > width);
        return text.substring(0, end) + "…";
    }

    private static String clip(String text) { return fit(text, 68); }
    private static Component icon(String text, TextColor color, Component field) {
        return Component.text(text + " ", color).append(field);
    }
    private static String value(Object value) { return value == null ? "—" : value.toString(); }
    private static Component title(String text) { return Component.text(text, GOLD); }
    private static String position(Integer value) { return value == null ? "—" : "#" + value; }
    private static Component separator() { return Component.text("  │  ", NamedTextColor.DARK_GRAY); }
    private static Component field(String label, Object value) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(String.valueOf(value), GOLD));
    }

    static Component renderFooter(NetworkRanking ranking) {
        var footer = Component.text().append(Component.newline())
                .append(Component.text("TOP 20 GRACZY", GOLD)).append(separator())
                .append(Component.text("TOP 20 GILDII", GOLD));
        int size = Math.max(ranking.players().size(), ranking.guilds().size());
        String[] players = new String[size];
        int width = 0;
        for (int i = 0; i < size; i++) {
            players[i] = row(ranking.players(), i);
            width = Math.max(width, textWidth(players[i]));
        }
        for (int i = 0; i < size; i++) {
            int padding = Math.max(1, (width - textWidth(players[i]) + 3) / 4);
            footer.append(Component.newline()).append(Component.text(players[i] + " ".repeat(padding), NamedTextColor.WHITE))
                    .append(separator()).append(Component.text(row(ranking.guilds(), i), NamedTextColor.WHITE));
        }
        if (size == 0) footer.append(Component.newline()).append(Component.text("Brak wyników", NamedTextColor.GRAY));
        return footer.append(Component.newline()).append(Component.text("Ranking według zabójstw", NamedTextColor.GRAY)).build();
    }

    private static String row(java.util.List<NetworkRanking.Entry> entries, int index) {
        if (index >= entries.size()) return "—";
        var entry = entries.get(index);
        String label = entry.label().length() <= 16 ? entry.label() : entry.label().substring(0, 15) + "…";
        return (index + 1) + ". " + label + "  " + entry.kills();
    }

    private static int textWidth(String text) {
        return text.chars().map(c -> switch (c) {
            case 'i', '.', '!' -> 2;
            case 'l' -> 3;
            case ' ', 'I', 't' -> 4;
            case 'f', 'k' -> 5;
            default -> 6;
        }).sum();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
    }
}
