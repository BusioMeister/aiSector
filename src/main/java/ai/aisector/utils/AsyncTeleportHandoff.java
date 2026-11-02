package ai.aisector.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps durable-state cleanup behind teleport confirmation and a current-session main-thread handoff. */
public final class AsyncTeleportHandoff {
    private AsyncTeleportHandoff() { }

    public static void start(Supplier<CompletableFuture<Boolean>> teleport, Function<Runnable, Boolean> scheduler,
                             BooleanSupplier currentSession, Runnable confirmed, Consumer<Throwable> failed) {
        CompletableFuture<Boolean> future;
        try { future = java.util.Objects.requireNonNull(teleport.get()); }
        catch (RuntimeException error) { future = CompletableFuture.failedFuture(error); }
        future.whenComplete((success, error) -> scheduler.apply(() -> {
            if (!currentSession.getAsBoolean()) return;
            if (error == null && Boolean.TRUE.equals(success)) confirmed.run();
            else failed.accept(error == null ? new IllegalStateException("Teleport was rejected") : error);
        }));
    }
}
