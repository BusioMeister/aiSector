package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ai.aisector.utils.BukkitTaskScheduler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BanListCommand implements CommandExecutor {
    private static final int MAX_SCAN_PER_CATEGORY = 5_000;
    private static final int MAX_DISPLAY_PER_CATEGORY = 200;
    private final SectorPlugin plugin;

    public BanListCommand(SectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.ban.list")) {
            sender.sendMessage(Messages.text("ban-list-command.brak-uprawnien"));
            return true;
        }
        sender.sendMessage(Messages.text("ban-list-command.pobieram-aktywne-bany"));
        plugin.getRedisManager().executeAsync(this::readBanList)
                .whenComplete((lines, error) -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().warning("Could not read ban list: " + error.getMessage());
                        sender.sendMessage(Messages.text("ban-list-command.nie-udalo-sie-pobrac-listy-banow"));
                        return;
                    }
                    if (lines != null) lines.forEach(sender::sendMessage);
                }));
        return true;
    }

    private List<String> readBanList(Jedis jedis) {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        appendBanSection(jedis, lines, now, "Aktywne bany graczy:", "bans:users", "ban:user:", false);
        appendBanSection(jedis, lines, now, "Aktywne bany IP:", "bans:ips", "ban:ip:", true);
        return lines;
    }

    private void appendBanSection(Jedis jedis, List<String> lines, long now, String title, String indexKey,
                                 String banPrefix, boolean ipBan) {
        lines.add("§6" + title);
        ScanParams scanParams = new ScanParams().count(100);
        String cursor = ScanParams.SCAN_POINTER_START;
        int scanned = 0;
        int displayed = 0;
        do {
            ScanResult<String> result = jedis.sscan(indexKey, cursor, scanParams);
            cursor = result.getCursor();
            for (String member : result.getResult()) {
                scanned++;
                String key = banPrefix + member;
                Map<String, String> ban = jedis.hgetAll(key);
                if (ban.isEmpty()) {
                    BanRedisScripts.removeIndexIfMissing(jedis, key, indexKey, member);
                    continue;
                }
                String expiresValue = ban.get("expires_at");
                long expires = parseExpiration(expiresValue, key);
                if (isExpired(expires, now)) {
                    BanRedisScripts.removeExpiredIfUnchanged(jedis, key, indexKey, member,
                            ban.get("ban_id"), expiresValue, ban.get("created_at"));
                    continue;
                }
                if (displayed >= MAX_DISPLAY_PER_CATEGORY) continue;
                displayed++;
                String name = valueOrDefault(ban.get("name"), member);
                String reason = valueOrDefault(ban.get("reason"), "Brak");
                String by = valueOrDefault(ban.get("banned_by"), "System");
                lines.add(Messages.text("ban-list-command.by", name, (ipBan ? " §7(IP)" : ""), formatExpiration(expires), reason, by));
            }
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor) && scanned < MAX_SCAN_PER_CATEGORY);

        if (displayed >= MAX_DISPLAY_PER_CATEGORY) {
            lines.add(Messages.text("ban-list-command.lista-ograniczona-do-wpisow-w-tej-kategorii", MAX_DISPLAY_PER_CATEGORY));
        }
        if (!ScanParams.SCAN_POINTER_START.equals(cursor) && scanned >= MAX_SCAN_PER_CATEGORY) {
            lines.add(Messages.text("ban-list-command.skanowanie-ograniczone-do-indeksow-uruchom-ponownie-aby", MAX_SCAN_PER_CATEGORY));
        }
    }

    private long parseExpiration(String value, String key) {
        if (value == null) return -1L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Treating malformed ban expiry as permanent for " + key);
            return -1L;
        }
    }

    private boolean isExpired(long expiresAt, long now) {
        return expiresAt != -1L && expiresAt <= now;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatExpiration(long expiresAt) {
        return expiresAt == -1 ? "perm" : Instant.ofEpochMilli(expiresAt).toString();
    }
}
