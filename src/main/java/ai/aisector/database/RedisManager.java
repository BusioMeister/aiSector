package ai.aisector.database;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns pooled Redis commands and dedicated, lifecycle-managed Pub/Sub connections. */
public final class RedisManager implements AutoCloseable {
    private final JedisPool jedisPool;
    private final String host;
    private final int port;
    private final String password;
    private final Consumer<String> errorLogger;
    private final List<JedisPubSub> subscribers = new CopyOnWriteArrayList<>();
    private final java.util.Set<JedisPubSub> patternSubscribers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Thread, Jedis> subscriptionConnections = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Thread> subscriptionThreads = new CopyOnWriteArrayList<>();
    private final ExecutorService commandExecutor = new ThreadPoolExecutor(2, 2, 0L,
            java.util.concurrent.TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4096), runnable -> {
                Thread thread = new Thread(runnable, "aiSector-redis-command");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean closed = new AtomicBoolean();

    public RedisManager(String host, int port, String password, Consumer<String> errorLogger) {
        this.host = Objects.requireNonNull(host, "host");
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Redis port must be between 1 and 65535");
        }
        this.port = port;
        this.password = password == null ? "" : password;
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(1);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTime(Duration.ofSeconds(60));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));

        this.jedisPool = this.password.isEmpty()
                ? new JedisPool(poolConfig, host, port, 2_000)
                : new JedisPool(poolConfig, host, port, 2_000, this.password);
    }

    public Jedis getJedis() {
        return jedisPool.getResource();
    }

    public CompletableFuture<String> getAsync(String key) {
        return executeAsync(jedis -> jedis.get(key));
    }

    public CompletableFuture<String> hgetAsync(String key, String field) {
        return executeAsync(jedis -> jedis.hget(key, field));
    }

    public CompletableFuture<Long> publishAsync(String channel, String message) {
        return executeAsync(jedis -> jedis.publish(channel, message));
    }

    public CompletableFuture<String> setAsync(String key, String value) {
        return executeAsync(jedis -> jedis.set(key, value));
    }

    public <T> CompletableFuture<T> executeAsync(Function<Jedis, T> command) {
        Objects.requireNonNull(command, "command");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("RedisManager is closed"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try (Jedis jedis = getJedis()) {
                    return command.apply(jedis);
                }
            }, commandExecutor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public void subscribe(JedisPubSub subscriber, String... channels) {
        startSubscription(subscriber, false, channels);
    }

    public void psubscribe(JedisPubSub subscriber, String... patterns) {
        startSubscription(subscriber, true, patterns);
    }

    private void startSubscription(JedisPubSub subscriber, boolean pattern, String... channels) {
        if (closed.get()) {
            throw new IllegalStateException("RedisManager is closed");
        }
        subscribers.add(subscriber);
        if (pattern) patternSubscribers.add(subscriber);
        Thread thread = new Thread(() -> {
            long retryDelayMillis = 1_000L;
            try {
                while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                    try (Jedis jedis = new Jedis(host, port)) {
                        subscriptionConnections.put(Thread.currentThread(), jedis);
                        if (closed.get() || Thread.currentThread().isInterrupted()) break;
                        if (!password.isEmpty()) {
                            jedis.auth(password);
                        }
                        if (pattern) {
                            jedis.psubscribe(subscriber, channels);
                        } else {
                            jedis.subscribe(subscriber, channels);
                        }
                        retryDelayMillis = 1_000L;
                    } catch (Exception exception) {
                        if (!closed.get()) {
                            errorLogger.accept("Redis subscription failed for " + String.join(",", channels)
                                    + "; retrying: " + exception.getMessage());
                        }
                    } finally { subscriptionConnections.remove(Thread.currentThread()); }
                    if (!closed.get()) {
                        Thread.sleep(retryDelayMillis);
                        retryDelayMillis = Math.min(retryDelayMillis * 2, 30_000L);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                subscribers.remove(subscriber);
                patternSubscribers.remove(subscriber);
                subscriptionThreads.remove(Thread.currentThread());
            }
        }, "aiSector-redis-subscription-" + Integer.toHexString(System.identityHashCode(subscriber)));
        subscriptionThreads.add(thread);
        thread.setDaemon(true);
        thread.start();
        if (closed.get()) thread.interrupt();
    }

    public void unsubscribe() {
        for (JedisPubSub subscriber : subscribers) {
            try {
                if (patternSubscribers.contains(subscriber)) subscriber.punsubscribe();
                else subscriber.unsubscribe();
            } catch (RuntimeException ignored) {
                // The subscriber may already be disconnected.
            }
        }
        subscribers.clear();
        List<Thread> threads = List.copyOf(subscriptionThreads);
        threads.forEach(Thread::interrupt);
        subscriptionConnections.values().forEach(connection -> {
            try { connection.disconnect(); } catch (RuntimeException ignored) { }
        });
        for (Thread thread : threads) {
            try {
                thread.join(2_500L);
                if (thread.isAlive()) errorLogger.accept("Redis subscription did not stop: " + thread.getName());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void closePool() {
        jedisPool.close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        unsubscribe();
        commandExecutor.shutdown();
        try {
            if (!commandExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                errorLogger.accept("Timed out waiting for queued Redis commands during shutdown; interrupting remaining tasks.");
                commandExecutor.shutdownNow();
                commandExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            commandExecutor.shutdownNow();
        }
        closePool();
    }
}
