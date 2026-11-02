package ai.aisector.user;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerKeyAsyncQueueTest {
    @Test
    void cancellingCallerViewDoesNotReleaseInternalBarrier() {
        PerKeyAsyncQueue<String> queue = new PerKeyAsyncQueue<>();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> first = queue.submit("p", () -> gate);
        first.cancel(false);
        AtomicBoolean started = new AtomicBoolean();
        CompletableFuture<Void> next = queue.submit("p", () -> {
            started.set(true);
            return CompletableFuture.completedFuture(null);
        });
        assertFalse(started.get());
        gate.complete(null);
        assertTrue(next.isDone());
        assertTrue(started.get());
    }

    @Test
    void reservesBarrierBeforeReentrantSubmission() {
        PerKeyAsyncQueue<String> queue = new PerKeyAsyncQueue<>();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        AtomicBoolean nestedStarted = new AtomicBoolean();
        queue.submit("p", () -> {
            queue.submit("p", () -> {
                nestedStarted.set(true);
                return CompletableFuture.completedFuture(null);
            });
            return gate;
        });
        assertFalse(nestedStarted.get());
        gate.complete(null);
        assertTrue(nestedStarted.get());
    }

    @Test
    void synchronousThrowAndNullFutureDoNotPoisonQueue() {
        PerKeyAsyncQueue<String> queue = new PerKeyAsyncQueue<>();
        assertTrue(queue.submit("p", () -> { throw new IllegalStateException(); }).isCompletedExceptionally());
        assertTrue(queue.submit("p", () -> null).isCompletedExceptionally());
        assertTrue(queue.submit("p", () -> CompletableFuture.completedFuture(null)).isDone());
    }

    @Test
    void limitsPendingWorkAndRecoversCapacityAfterCompletion() {
        PerKeyAsyncQueue<String> queue = new PerKeyAsyncQueue<>();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        for (int i = 0; i < 2048; i++) queue.submit("p" + i, () -> gate);
        assertTrue(queue.submit("overflow", () -> gate).isCompletedExceptionally());
        gate.complete(null);
        assertTrue(queue.submit("free", () -> CompletableFuture.completedFuture(null)).isDone());
    }

    @Test
    void waitsForEarlierOperationOnSameKey() {
        PerKeyAsyncQueue<UUID> queue = new PerKeyAsyncQueue<>();
        UUID player = UUID.randomUUID();
        CompletableFuture<Void> firstGate = new CompletableFuture<>();
        AtomicBoolean secondStarted = new AtomicBoolean();

        CompletableFuture<Void> first = queue.submit(player, () -> firstGate);
        CompletableFuture<Void> second = queue.submit(player, () -> {
            secondStarted.set(true);
            return CompletableFuture.completedFuture(null);
        });

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertFalse(secondStarted.get());

        firstGate.complete(null);

        assertTrue(first.isDone());
        assertTrue(second.isDone());
        assertTrue(secondStarted.get());
    }

    @Test
    void operationsForDifferentKeysCanRunConcurrently() {
        PerKeyAsyncQueue<UUID> queue = new PerKeyAsyncQueue<>();
        CompletableFuture<Void> firstGate = new CompletableFuture<>();
        AtomicBoolean otherStarted = new AtomicBoolean();

        CompletableFuture<Void> first = queue.submit(UUID.randomUUID(), () -> firstGate);
        CompletableFuture<Void> other = queue.submit(UUID.randomUUID(), () -> {
            otherStarted.set(true);
            return CompletableFuture.completedFuture(null);
        });

        assertFalse(first.isDone());
        assertTrue(other.isDone());
        assertTrue(otherStarted.get());
        firstGate.complete(null);
    }

    @Test
    void failedOperationDoesNotBlockLaterOperationsForKey() {
        PerKeyAsyncQueue<UUID> queue = new PerKeyAsyncQueue<>();
        UUID player = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Void> failed = queue.submit(player,
                () -> CompletableFuture.failedFuture(new IllegalStateException("write failed")));
        CompletableFuture<Void> next = queue.submit(player, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        assertTrue(failed.isCompletedExceptionally());
        assertTrue(next.isDone());
        assertEquals(1, calls.get());
    }
}
