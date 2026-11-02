package ai.aisector.commands;

import ai.aisector.shared.RedisChannels;
import redis.clients.jedis.Jedis;

import java.util.List;

/** Atomic index/hash maintenance for ban records shared by several Paper backends. */
final class BanRedisScripts {
    private static final String REMOVE_SCRIPT = "local removed = redis.call('del', KEYS[1]); "
            + "redis.call('srem', KEYS[2], ARGV[1]); return removed";
    private static final String REMOVE_INDEX_IF_MISSING_SCRIPT = "if redis.call('exists', KEYS[1]) == 0 then "
            + "return redis.call('srem', KEYS[2], ARGV[1]); end; return 0";
    private static final String REMOVE_EXPIRED_IF_UNCHANGED_SCRIPT = "local currentId = "
            + "redis.call('hget', KEYS[1], 'ban_id'); "
            + "local unchanged = (ARGV[1] ~= '' and currentId == ARGV[1]) or "
            + "(ARGV[1] == '' and not currentId "
            + "and redis.call('hget', KEYS[1], 'expires_at') == ARGV[2] "
            + "and (redis.call('hget', KEYS[1], 'created_at') or '') == ARGV[3]); "
            + "if unchanged then redis.call('del', KEYS[1]); redis.call('srem', KEYS[2], ARGV[4]); "
            + "return 1; end; return 0";
    private static final String WRITE_BAN_SCRIPT = "local hashType = redis.call('type', KEYS[1]).ok; "
            + "local indexType = redis.call('type', KEYS[2]).ok; "
            + "if hashType ~= 'none' and hashType ~= 'hash' then return redis.error_reply('ban key has wrong type'); end; "
            + "if indexType ~= 'none' and indexType ~= 'set' then return redis.error_reply('ban index has wrong type'); end; "
            + "redis.call('hset', KEYS[1], 'name', ARGV[2], 'reason', ARGV[3], 'banned_by', ARGV[4], "
            + "'created_at', ARGV[5], 'expires_at', ARGV[6], 'type', ARGV[7], 'ban_id', ARGV[8]); "
            + "redis.call('sadd', KEYS[2], ARGV[1]); redis.call('publish', KEYS[3], ARGV[9]); "
            + "if ARGV[10] ~= '' then redis.call('publish', KEYS[4], ARGV[10]); end; return 1";

    private BanRedisScripts() { }

    static long writeBan(Jedis jedis, String hashKey, String indexKey, String member, String type,
                         String name, String reason, String bannedBy, long createdAt, long expiresAt,
                         String banId, String broadcastPayload, String kickPayload) {
        return number(jedis.eval(WRITE_BAN_SCRIPT,
                List.of(hashKey, indexKey, RedisChannels.BAN_BROADCAST, RedisChannels.BAN_KICK),
                List.of(member, name, reason, bannedBy, Long.toString(createdAt), Long.toString(expiresAt),
                        type, banId, broadcastPayload, kickPayload == null ? "" : kickPayload)));
    }

    static long remove(Jedis jedis, String hashKey, String indexKey, String member) {
        return number(jedis.eval(REMOVE_SCRIPT, List.of(hashKey, indexKey), List.of(member)));
    }

    static long removeIndexIfMissing(Jedis jedis, String hashKey, String indexKey, String member) {
        return number(jedis.eval(REMOVE_INDEX_IF_MISSING_SCRIPT, List.of(hashKey, indexKey), List.of(member)));
    }

    static long removeExpiredIfUnchanged(Jedis jedis, String hashKey, String indexKey, String member,
                                         String banId, String expiresAt, String createdAt) {
        return number(jedis.eval(REMOVE_EXPIRED_IF_UNCHANGED_SCRIPT, List.of(hashKey, indexKey),
                List.of(banId == null ? "" : banId, expiresAt, createdAt == null ? "" : createdAt, member)));
    }

    private static long number(Object result) {
        return result instanceof Number value ? value.longValue() : 0L;
    }
}
