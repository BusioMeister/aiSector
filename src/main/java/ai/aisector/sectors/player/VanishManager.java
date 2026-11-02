package ai.aisector.sectors.player;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.user.PerKeyAsyncQueue;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VanishManager {

    private final SectorPlugin plugin;
    private final VanishTagManager tagManager; // <-- NOWE POLE
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final VanishNetworkStateCache networkVanishes = new VanishNetworkStateCache();
    private record PendingVanish(UUID uuid, String name, String action, String json) { }
    private final Map<UUID, PendingVanish> pendingPublications = new ConcurrentHashMap<>();
    private final PerKeyAsyncQueue<UUID> publications = new PerKeyAsyncQueue<>();
    private volatile boolean snapshotInFlight;
    private int reconciliationTicks;
    private long lastSnapshotWarning;
    private static final String STORE_AND_PUBLISH = "if ARGV[3] == 'VANISH' then "
            + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) else redis.call('hdel', KEYS[1], ARGV[1]) end; "
            + "return redis.call('publish', KEYS[2], ARGV[4])";
    private static final String READ_SNAPSHOT = "if redis.call('hlen', KEYS[1]) > 4096 then "
            + "return redis.error_reply('vanish snapshot exceeds limit') end; "
            + "local a = redis.call('hgetall', KEYS[1]); for i=1,#a,2 do "
            + "if string.len(a[i]) ~= 36 or string.len(a[i+1]) > 16 then "
            + "return redis.error_reply('invalid vanish entry bounds') end end; return a";

    public VanishManager(SectorPlugin plugin) {
        this.plugin = plugin;
        this.tagManager = new VanishTagManager(); // Inicjalizujemy tag manager
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            reconcileLocalViews();
            if (reconciliationTicks++ % 5 == 0) {
                pendingPublications.values().forEach(this::publishPending);
                refreshSnapshot();
            }
        }, 20L, 20L);
    }

    public static boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    // Nowa metoda do pobierania listy ukrytych graczy
    public static Set<Player> getVanishedPlayers() {
        return vanishedPlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public void vanish(Player admin) {
        networkVanishes.clear(admin.getUniqueId());
        vanishedPlayers.add(admin.getUniqueId());
        tagManager.setVanishedTag(admin.getName()); // <-- APLIKUJ TAG
        broadcastVanishState(admin, "VANISH"); // <-- ROZSYŁAJ INFO

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("aisector.command.vanish.see")) {
                onlinePlayer.hidePlayer(plugin, admin);
            }
        }
    }

    public void unvanish(Player admin) {
        networkVanishes.clear(admin.getUniqueId());
        vanishedPlayers.remove(admin.getUniqueId());
        tagManager.removeVanishedTag(admin.getName()); // <-- USUŃ TAG
        broadcastVanishState(admin, "UNVANISH"); // <-- ROZSYŁAJ INFO

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, admin);
        }
    }

    public void handlePlayerJoin(Player joiningPlayer) {
        // Pokazujemy dołączającemu adminowi tagi graczy, którzy już są w vanish
        if (joiningPlayer.hasPermission("aisector.command.vanish.see")) {
            tagManager.updateTagsForJoiningStaff(joiningPlayer);
            networkVanishes.snapshot().values().forEach(name -> tagManager.setVanishedTag(name));
        }

        // Ukrywamy przed dołączającym graczem adminów będących w vanish
        for (UUID vanishedUUID : vanishedPlayers) {
            Player vanishedAdmin = Bukkit.getPlayer(vanishedUUID);
            if (vanishedAdmin != null) {
                if (!joiningPlayer.hasPermission("aisector.command.vanish.see")) {
                    joiningPlayer.hidePlayer(plugin, vanishedAdmin);
                }
            }
        }
    }

    // Nowa metoda do rozsyłania statusu po sieci
    private void broadcastVanishState(Player player, String action) {
        String playerName = player.getName();
        JsonObject data = new JsonObject();
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", playerName); // Przesyłamy też nick
        data.addProperty("action", action);

        PendingVanish update = new PendingVanish(player.getUniqueId(), playerName, action, data.toString());
        pendingPublications.put(update.uuid(), update);
        publishPending(update);
    }

    private void publishPending(PendingVanish update) {
        publications.submitResult(update.uuid(), () -> plugin.getRedisManager().executeAsync(jedis ->
                jedis.eval(STORE_AND_PUBLISH, java.util.List.of(RedisChannels.VANISH_STATE, RedisChannels.VANISH_UPDATE),
                        java.util.List.of(update.uuid().toString(), update.name(), update.action(), update.json()))))
                .whenComplete((ignored, error) -> {
                    if (error == null) pendingPublications.remove(update.uuid(), update);
                    else warnSnapshot("Could not publish vanish state; retry scheduled");
                });
    }

    private void refreshSnapshot() {
        if (snapshotInFlight) return;
        snapshotInFlight = true;
        long observedRevision = networkVanishes.revision();
        plugin.getRedisManager().executeAsync(jedis -> {
            Object result = jedis.eval(READ_SNAPSHOT, java.util.List.of(RedisChannels.VANISH_STATE), java.util.List.of());
            if (!(result instanceof java.util.List<?> entries) || entries.size() % 2 != 0)
                throw new IllegalStateException("Invalid vanish snapshot response");
            Map<String, String> values = new java.util.HashMap<>();
            for (int i = 0; i < entries.size(); i += 2) {
                if (!(entries.get(i) instanceof String uuid) || !(entries.get(i + 1) instanceof String name))
                    throw new IllegalStateException("Invalid vanish snapshot entry");
                values.put(uuid, name);
            }
            return values;
        }).whenComplete((values, error) -> {
            if (!BukkitTaskScheduler.execute(plugin, () -> {
                snapshotInFlight = false;
                if (error != null) { warnSnapshot("Could not refresh vanish snapshot"); return; }
                try {
                    Map<UUID, String> parsed = new java.util.HashMap<>();
                    values.forEach((uuid, name) -> {
                        UUID id = UUID.fromString(uuid);
                        if (!id.toString().equals(uuid)) throw new IllegalArgumentException("Invalid vanish UUID");
                        parsed.put(id, name);
                    });
                    networkVanishes.replaceSnapshot(observedRevision, parsed, java.util.Set.copyOf(vanishedPlayers));
                    reconcileLocalViews();
                } catch (RuntimeException invalid) { warnSnapshot("Ignoring invalid vanish snapshot"); }
            })) snapshotInFlight = false;
        });
    }

    private synchronized void warnSnapshot(String message) {
        long now = System.nanoTime();
        if (now - lastSnapshotWarning > 30_000_000_000L) {
            lastSnapshotWarning = now;
            plugin.getLogger().warning(message);
        }
    }

    private void reconcileLocalViews() {
        java.util.Set<String> names = new java.util.HashSet<>(networkVanishes.snapshot().values());
        for (UUID uuid : vanishedPlayers) {
            Player hidden = Bukkit.getPlayer(uuid);
            if (hidden == null) continue;
            names.add(hidden.getName());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.hasPermission("aisector.command.vanish.see")) viewer.showPlayer(plugin, hidden);
                else viewer.hidePlayer(plugin, hidden);
            }
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) tagManager.reconcileViewer(viewer, names);
    }

    /** Apply a validated update received from another backend (must run on the server thread). */
    public void applyNetworkVanish(UUID playerUuid, String playerName, boolean vanished) {
        if (vanished) {
            networkVanishes.setVanished(playerUuid, playerName);
            tagManager.setVanishedTag(playerName);
        } else {
            networkVanishes.clear(playerUuid);
            tagManager.removeVanishedTag(playerName);
        }
    }

    /** Cleans only this backend's live Bukkit state; disconnect is not an unvanish transition. */
    public void handlePlayerQuit(Player player) {
        pendingPublications.remove(player.getUniqueId());
        vanishedPlayers.remove(player.getUniqueId());
        tagManager.removeVanishedTag(player.getName());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, player);
        }
    }
}
