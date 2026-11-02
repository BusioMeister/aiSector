package ai.aisector.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncTeleportHandoffTest {
    @Test
    void cleanupWaitsForConfirmedTeleportAndMainThreadHandoff() {
        var teleport = new CompletableFuture<Boolean>();
        List<Runnable> scheduled = new ArrayList<>();
        AtomicBoolean cleaned = new AtomicBoolean();
        AsyncTeleportHandoff.start(() -> teleport, task -> scheduled.add(task), () -> true,
                () -> cleaned.set(true), error -> fail(error));
        assertFalse(cleaned.get());
        teleport.complete(true);
        assertFalse(cleaned.get());
        scheduled.getFirst().run();
        assertTrue(cleaned.get());
    }

    @Test
    void falseAndFailedTeleportsRetainDurableState() {
        for (var teleport : List.of(CompletableFuture.completedFuture(false),
                CompletableFuture.<Boolean>failedFuture(new IllegalStateException("failed")))) {
            AtomicBoolean failed = new AtomicBoolean();
            AsyncTeleportHandoff.start(() -> teleport, task -> { task.run(); return true; }, () -> true,
                    () -> fail("Must retain state"), error -> failed.set(true));
            assertTrue(failed.get());
        }
    }

    @Test
    void reconnectOrShutdownCannotConsumeJoinKeys() {
        AtomicBoolean current = new AtomicBoolean(true);
        List<Runnable> scheduled = new ArrayList<>();
        AsyncTeleportHandoff.start(() -> CompletableFuture.completedFuture(true), scheduled::add, current::get,
                () -> fail("Stale session cleanup"), error -> fail(error));
        current.set(false);
        scheduled.getFirst().run();
        AsyncTeleportHandoff.start(() -> CompletableFuture.completedFuture(true), task -> false, () -> true,
                () -> fail("Shutdown cleanup"), error -> fail(error));
    }

    @Test
    void synchronousStartFailureUsesFailureHandoff() {
        AtomicBoolean failed = new AtomicBoolean();
        AsyncTeleportHandoff.start(() -> { throw new IllegalStateException("start"); },
                task -> { task.run(); return true; }, () -> true, () -> fail("cleanup"), error -> failed.set(true));
        assertTrue(failed.get());
    }
}
