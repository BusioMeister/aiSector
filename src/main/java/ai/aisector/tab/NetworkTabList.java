package ai.aisector.tab;

import ai.aisector.SectorPlugin;
import ai.aisector.skills.MiningLevelManager;
import com.mongodb.client.model.Projections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkTabList implements Listener {
    private static final Component BRAND = MiniMessage.miniMessage()
            .deserialize("<gradient:#ff3838:#ffca55:#ff3838><bold>✦ LASTPLAY.NET ✦</bold></gradient>");
    private static final TextColor GOLD = TextColor.color(0xffca55);
    private record Snapshot(NetworkRanking ranking, Component footer) {}
    private record Sent(Component header, Component footer) {}

    private final SectorPlugin plugin;
    private final MiningLevelManager ranks;
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    public NetworkTabList(SectorPlugin plugin, MiningLevelManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
    }

    public void start() {
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
            Component header = header(player, current == null ? null : current.ranking());
            Sent next = new Sent(header, footer);
            if (!next.equals(sent.get(player.getUniqueId()))) {
                player.sendPlayerListHeaderAndFooter(header, footer);
                sent.put(player.getUniqueId(), next);
            }
        }
    }

    private Component header(Player player, NetworkRanking ranking) {
        var user = plugin.getUserManager().getUser(player);
        if (user == null) return BRAND.append(Component.newline())
                .append(Component.text("Wczytywanie profilu…", NamedTextColor.GRAY));
        String primaryGroup = ranks.getPrimaryGroup(player);
        String rank = primaryGroup == null || primaryGroup.isBlank() || primaryGroup.equalsIgnoreCase("default")
                ? (player.isOp() ? "Operator" : "Gracz") : primaryGroup;
        UUID id = player.getUniqueId();
        var guild = user.hasGuild() ? plugin.getGuildManager().getGuildCached(user.getGuildTag()) : null;
        String role = guild == null ? null : guild.roleOf(id);
        String tag = role == null ? null : guild.getTag().toUpperCase(Locale.ROOT);
        String guildRole = role == null ? "—" : id.equals(guild.getDeputy()) && !"OWNER".equals(role)
                ? "Zastępca" : switch (role) {
            case "OWNER" -> "Lider";
            case "MOD" -> "Moderator";
            default -> "Członek";
        };
        return BRAND.append(Component.newline())
                .append(field("Nick: ", player.getName())).append(separator()).append(field("Ranga: ", rank))
                .append(Component.newline()).append(field("Zabójstwa: ", user.getKills())).append(separator())
                .append(field("Śmierci: ", user.getDeaths())).append(separator())
                .append(field("Ranking: ", position(ranking == null ? null : ranking.playerPositions().get(id.toString()))))
                .append(Component.newline()).append(field("Gildia: ", tag == null ? "Brak" : "[" + tag + "]"))
                .append(separator()).append(field("Rola: ", guildRole)).append(separator())
                .append(field("Członkowie: ", role == null ? "—" : guild.getMembers().size()))
                .append(separator()).append(field("Ranking gildii: ",
                        position(ranking == null || tag == null ? null : ranking.guildPositions().get(tag))))
                .append(Component.newline());
    }

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
