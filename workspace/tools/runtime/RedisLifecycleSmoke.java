import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import redis.clients.jedis.JedisPubSub;

public class RedisLifecycleSmoke {
    private static JedisPubSub listener(CountDownLatch ready) {
        return new JedisPubSub() {
            public void onSubscribe(String channel, int count) { ready.countDown(); }
            public void onPSubscribe(String channel, int count) { ready.countDown(); }
        };
    }
    public static void main(String[] args) throws Exception {
        List<String> warnings = new CopyOnWriteArrayList<>();
        CountDownLatch ready = new CountDownLatch(4);
        try (var paper = new ai.aisector.database.RedisManager("127.0.0.1", 6380, "", warnings::add);
             var proxy = new ai.velocitysector.RedisManager("127.0.0.1", 6380, "", warnings::add)) {
            paper.subscribe(listener(ready), "rewrite:smoke:lifecycle");
            paper.psubscribe(listener(ready), "rewrite:smoke:lifecycle:*");
            proxy.subscribe(listener(ready), "rewrite:smoke:lifecycle");
            proxy.psubscribe(listener(ready), "rewrite:smoke:lifecycle:*");
            if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("Subscriptions not ready");
        }
        if (!warnings.isEmpty()) throw new AssertionError(warnings.toString());
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("aiSector-")
                    && thread.getName().contains("redis-subscription")) throw new AssertionError("Leaked subscriber " + thread);
        }
        System.out.println("REDIS_LIFECYCLE_SMOKE_OK: subscribe/psubscribe close for Paper and Velocity");
    }
}
