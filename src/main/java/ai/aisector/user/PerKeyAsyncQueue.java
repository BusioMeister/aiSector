package ai.aisector.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Serializes asynchronous operations for one key without blocking their caller. */
public final class PerKeyAsyncQueue<K> {
    private final Map<K, CompletableFuture<Void>> tails = new HashMap<>();
    private int pending;
    private static final int MAX_PENDING = 2048;

    public CompletableFuture<Void> submit(K key, Supplier<CompletableFuture<Void>> operation) {
        return submitResult(key, operation);
    }

    public <T> CompletableFuture<T> submitResult(K key,
                                                        Supplier<CompletableFuture<T>> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");

        CompletableFuture<Void> previous;
        CompletableFuture<Void> nextTail = new CompletableFuture<>();
        synchronized (this) {
            if (pending >= MAX_PENDING) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("Profile operation queue is full"));
            }
            pending++;
            previous = tails.put(key, nextTail);
        }
        // Reserve the tail before invoking user code (which may itself enqueue another operation).
        CompletableFuture<T> result = (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                .handle((ignored, error) -> null)
                .thenCompose(ignored -> Objects.requireNonNull(operation.get(), "operation future"));
        result.whenComplete((ignored, error) -> {
            synchronized (this) {
                tails.remove(key, nextTail);
                pending--;
            }
            nextTail.complete(null);
        });
        // A caller cancelling/completing its view must not release the internal serialization barrier.
        return result.copy();
    }
}
