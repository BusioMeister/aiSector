package ai.aisector.database;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;

public class RedisManager {

    private final JedisPool jedisPool;
    private final String host;
    private final int port;
    private JedisPubSub currentSubscriber;

    public RedisManager(String host, int port) {
        this.host = host;
        this.port = port;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);

        this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
    }

    public Jedis getJedis() {
        return jedisPool.getResource();
    }

    public void releaseJedis(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }

    // WAŻNE: Nie korzystamy z puli w subskrypcji!
    public void subscribe(JedisPubSub subscriber, String... channels) {
        this.currentSubscriber = subscriber;
        new Thread(() -> {
            try (Jedis jedis = new Jedis(host, port)) {
                jedis.subscribe(subscriber, channels);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Redis-Subscriber-Thread").start();
    }

    public void closePool() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
    public void unsubscribe() {
        if (currentSubscriber != null) {
            currentSubscriber.unsubscribe();
        }
    }
}
