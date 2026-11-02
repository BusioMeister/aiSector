package ai.aisector.resolver;

import ai.aisector.SectorPlugin;
import com.mongodb.client.model.Filters;
import org.bukkit.Bukkit;
import org.bson.Document;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

public class NameResolver {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final int MAX_MOJANG_CACHE_ENTRIES = 4096;
    private static final long POSITIVE_CACHE_TTL_NANOS = Duration.ofMinutes(30).toNanos();
    private static final long NEGATIVE_CACHE_TTL_NANOS = Duration.ofSeconds(30).toNanos();
    private static final ConcurrentHashMap<String, CachedUuid> MOJANG_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Optional<UUID>>> MOJANG_LOOKUPS =
            new ConcurrentHashMap<>();
    private static final Semaphore MOJANG_REQUEST_LIMIT = new Semaphore(8);

    private record CachedUuid(Optional<UUID> value, long expiresAtNanos) { }

    private final SectorPlugin plugin;
    public NameResolver(SectorPlugin plugin) { this.plugin = plugin; }

    public CompletableFuture<Optional<UUID>> resolveUuidAsync(String input) {
        if (input == null || input.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<UUID> explicitUuid = parseUuid(input);
        if (explicitUuid.isPresent()) {
            return CompletableFuture.completedFuture(explicitUuid);
        }
        String username = input.trim();
        if (!isValidUsername(username)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        org.bukkit.entity.Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }

        String nameLower = username.toLowerCase(Locale.ROOT);
        return plugin.getRedisManager().hgetAsync("global:players", nameLower)
                .handle((uuidValue, error) -> error == null ? parseUuid(uuidValue) : Optional.<UUID>empty())
                .thenCompose(redisUuid -> {
                    if (redisUuid.isPresent()) {
                        return CompletableFuture.completedFuture(redisUuid);
                    }
                    return plugin.getMongoDBManager()
                            .findOneAsync("users", Filters.regex("name",
                                     Pattern.compile("^" + Pattern.quote(username) + "$", Pattern.CASE_INSENSITIVE)))
                            .handle((document, error) -> {
                                if (error == null && document != null) {
                                    return parseUuid(document.getString("uuid"));
                                }
                                return Optional.<UUID>empty();
                            });
                })
                .thenCompose(mongoUuid -> mongoUuid.isPresent()
                        ? CompletableFuture.completedFuture(mongoUuid)
                         : offlineUuidAsync(username));
    }

    static boolean isValidUsername(String username) {
        return username != null && MINECRAFT_USERNAME.matcher(username).matches();
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private CompletableFuture<Optional<UUID>> offlineUuidAsync(String playerName) {
        String username = playerName.trim();
        if (!isValidUsername(username)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String cacheKey = username.toLowerCase(Locale.ROOT);
        long now = System.nanoTime();
        CachedUuid cached = MOJANG_CACHE.get(cacheKey);
        if (cached != null) {
            if (now < cached.expiresAtNanos()) return CompletableFuture.completedFuture(cached.value());
            MOJANG_CACHE.remove(cacheKey, cached);
        }

        CompletableFuture<Optional<UUID>> existing = MOJANG_LOOKUPS.get(cacheKey);
        if (existing != null) return existing;
        if (!MOJANG_REQUEST_LIMIT.tryAcquire()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<UUID>> result = new CompletableFuture<>();
        existing = MOJANG_LOOKUPS.putIfAbsent(cacheKey, result);
        if (existing != null) {
            MOJANG_REQUEST_LIMIT.release();
            return existing;
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(
                            URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
        } catch (RuntimeException exception) {
            finishMojangLookup(cacheKey, result, Optional.empty());
            return result;
        }

        try {
            HTTP_CLIENT.sendAsync(request, boundedStringBodyHandler(16_384))
                    .thenApply(response -> {
                        if (response.statusCode() != 200 || response.body() == null
                                || response.body().length() > 16_384) {
                            return Optional.<UUID>empty();
                        }
                        try {
                            String id = new JSONObject(response.body()).optString("id", "");
                            if (!id.matches("^[0-9a-fA-F]{32}$")) return Optional.<UUID>empty();
                            String uuid = id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                                    + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
                            return Optional.of(UUID.fromString(uuid));
                        } catch (RuntimeException exception) {
                            return Optional.<UUID>empty();
                        }
                    })
                    .exceptionally(error -> Optional.<UUID>empty())
                    .whenComplete((resolved, error) -> finishMojangLookup(cacheKey, result,
                            error == null && resolved != null ? resolved : Optional.empty()));
        } catch (RuntimeException exception) {
            finishMojangLookup(cacheKey, result, Optional.empty());
        }
        return result;
    }

    private static HttpResponse.BodyHandler<String> boundedStringBodyHandler(int maxBytes) {
        return responseInfo -> new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<String> body = new CompletableFuture<>();
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();
            private Flow.Subscription subscription;

            @Override
            public CompletionStage<String> getBody() {
                return body;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(List<ByteBuffer> buffers) {
                for (ByteBuffer buffer : buffers) {
                    int remaining = buffer.remaining();
                    if (output.size() + remaining > maxBytes) {
                        subscription.cancel();
                        body.completeExceptionally(new IllegalArgumentException("Mojang response exceeded limit"));
                        return;
                    }
                    byte[] chunk = new byte[remaining];
                    buffer.get(chunk);
                    output.write(chunk, 0, chunk.length);
                }
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(output.toString(StandardCharsets.UTF_8));
            }
        };
    }

    private static void finishMojangLookup(String cacheKey, CompletableFuture<Optional<UUID>> result,
                                           Optional<UUID> resolved) {
        long ttl = resolved.isPresent() ? POSITIVE_CACHE_TTL_NANOS : NEGATIVE_CACHE_TTL_NANOS;
        cacheMojangResult(cacheKey, new CachedUuid(resolved, System.nanoTime() + ttl));
        MOJANG_LOOKUPS.remove(cacheKey, result);
        MOJANG_REQUEST_LIMIT.release();
        result.complete(resolved);
    }

    private static synchronized void cacheMojangResult(String key, CachedUuid value) {
        if (MOJANG_CACHE.size() >= MAX_MOJANG_CACHE_ENTRIES && !MOJANG_CACHE.containsKey(key)) {
            long now = System.nanoTime();
            MOJANG_CACHE.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtNanos());
            if (MOJANG_CACHE.size() >= MAX_MOJANG_CACHE_ENTRIES) {
                MOJANG_CACHE.keySet().stream().findFirst().ifPresent(MOJANG_CACHE::remove);
            }
        }
        MOJANG_CACHE.put(key, value);
    }

    public CompletableFuture<Optional<String>> resolveLastIpAsync(UUID uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(Optional.empty());
        return plugin.getMongoDBManager().findOneAsync("users", new Document("uuid", uuid.toString()))
                .handle((document, error) -> {
                    if (error != null || document == null) return Optional.<String>empty();
                    String ip = document.getString("last_ip");
                    return ip == null || ip.isBlank() ? Optional.<String>empty() : Optional.of(ip);
                });
    }
}
