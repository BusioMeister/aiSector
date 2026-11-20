package ai.aisector.tab;

import ai.aisector.SectorPlugin;
import ai.aisector.skills.MiningLevelManager;
import com.mongodb.client.model.Projections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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
    private static final String LAYOUT_CHANNEL = "aisector:tab_layout";
    private static final Component EMPTY = Component.text("\u00a0");
    private static final Component WIDE = Component.text("\u00a0".repeat(30));
    private static final Component BRAND = MiniMessage.miniMessage()
            .deserialize("<gradient:#ff3838:#ffca55:#ff3838><bold>✦ LASTPLAY.NET ✦</bold></gradient>");
    private static final Component GRID_HEADER = BRAND.append(Component.newline())
            .append(Component.text("TOP 20 GRACZY   ◆   TOP 20 GILDII", NamedTextColor.GRAY));
    private static final Component GRID_FOOTER = Component.text("LastPlay.net  •  Ranking według zabójstw",
            NamedTextColor.GRAY);
    private static final TextColor GOLD = TextColor.color(0xffca55);
    private record Snapshot(NetworkRanking ranking, Component footer) {}
    private record Sent(Component header, Component footer) {}
    private record Stats(String rank, String tag, String role, int members, Integer playerRank, Integer guildRank,
                         Integer kills, Integer deaths) {}

    private final SectorPlugin plugin;
    private final MiningLevelManager ranks;
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final Map<UUID, List<Component>> sentSlots = new HashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    public NetworkTabList(SectorPlugin plugin, MiningLevelManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
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
            Sent next = fixed ? new Sent(GRID_HEADER, GRID_FOOTER) : new Sent(header(player, ranking), footer);
            if (!next.equals(sent.get(player.getUniqueId()))) {
                player.sendPlayerListHeaderAndFooter(next.header(), next.footer());
                sent.put(player.getUniqueId(), next);
            }
            if (fixed) {
                List<Component> rows = slots(player, ranking);
                if (!rows.equals(sentSlots.get(player.getUniqueId())) && sendRows(player, rows)) {
                    sentSlots.put(player.getUniqueId(), rows);
                }
            }
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
        result.set(0, title("INFORMACJE O TOBIE"));
        result.set(2, field("Nick: ", player.getName()));
        result.set(3, field("Ranga: ", clip(stats.rank())));
        result.set(4, field("Zabójstwa: ", value(stats.kills())));
        result.set(5, field("Śmierci: ", value(stats.deaths())));
        result.set(6, field("Ranking: ", position(stats.playerRank())));
        result.set(8, title("TWÓJ SEKTOR"));
        result.set(10, field("Sektor: ", plugin.getConfig().getString("this-sector-name", "—")));
        result.set(11, field("Gracze na sektorze: ", Bukkit.getOnlinePlayers().stream().filter(player::canSee).count()));
        result.set(17, WIDE);
        result.set(60, title("INFORMACJE O GILDII"));
        result.set(62, field("Gildia: ", stats.tag() == null ? "Brak" : clip(stats.tag())));
        result.set(63, field("Rola: ", stats.role()));
        result.set(64, field("Członkowie: ", stats.members()));
        result.set(65, field("Ranking gildii: ", position(stats.guildRank())));
        result.set(68, title("LASTPLAY.NET"));
        result.set(70, Component.text("/g info", NamedTextColor.GRAY));
        result.set(71, Component.text("/spawn", NamedTextColor.GRAY));
        for (int i = 0; i < 20; i++) {
            result.set(20 + i, rankingRow(ranking == null ? List.of() : ranking.players(), i));
            result.set(40 + i, rankingRow(ranking == null ? List.of() : ranking.guilds(), i));
        }
        return result;
    }

    private boolean sendRows(Player player, List<Component> rows) {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeByte(1);
            for (Component row : rows) output.writeUTF(GsonComponentSerializer.gson().serialize(row));
        } catch (IOException error) {
            plugin.getLogger().warning("Could not encode TAB layout: " + error.getMessage());
            return false;
        }
        player.sendPluginMessage(plugin, LAYOUT_CHANNEL, buffer.toByteArray());
        return true;
    }

    private static Component rankingRow(List<NetworkRanking.Entry> entries, int index) {
        var row = Component.text("#" + (index + 1) + "  ", index == 0 ? GOLD : NamedTextColor.GRAY);
        if (index >= entries.size()) return row.append(Component.text("—", NamedTextColor.DARK_GRAY));
        var entry = entries.get(index);
        return row.append(Component.text(clip(entry.label()), NamedTextColor.WHITE))
                .append(Component.text("  " + entry.kills(), GOLD));
    }

    private static String clip(String text) { return text.length() <= 16 ? text : text.substring(0, 15) + "…"; }
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
        sentSlots.remove(event.getPlayer().getUniqueId());
    }
}
