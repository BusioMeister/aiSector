package ai.aisector;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public class RedisManager {

    private JedisPool jedisPool;

    // Konstruktor z parametrami (adres Redis i port)
    public RedisManager(String host, int port) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);  // Maksymalna liczba połączeń
        poolConfig.setMaxIdle(128);   // Maksymalna liczba połączeń w stanie bezczynności
        poolConfig.setMinIdle(16);    // Minimalna liczba połączeń w stanie bezczynności
        poolConfig.setTestOnBorrow(true);  // Sprawdza poprawność połączenia przed jego użyciem
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);

        // Ustawienia połączenia do Redis z dynamicznymi parametrami
        this.jedisPool = new JedisPool(poolConfig, host, port, 2000);  // Timeout 2000 ms
    }

    // Pobranie zasobu Jedis
    public Jedis getJedis() {
        return jedisPool.getResource();
    }

    // Zwalnianie zasobu Jedis po użyciu
    public void releaseJedis(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }

    // Opcjonalnie - metoda do zamknięcia puli po zakończeniu pracy
    public void closePool() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
