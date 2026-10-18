package com.nihongo.staff.service.monitor.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MetricSnapshotCacheTest {
    @Test
    void expiresAfterTheReadCompletesAndInvalidatesOnConfigurationChange() {
        var time = new AtomicLong();
        var cache = new MetricSnapshotCache<Long, Integer>(time::get);
        var loads = new AtomicInteger();
        assertEquals(1, cache.get(1L, id -> {
            time.addAndGet(Duration.ofSeconds(10).toNanos());
            return loads.incrementAndGet();
        }));
        time.addAndGet(Duration.ofSeconds(2).toNanos());
        assertEquals(1, cache.get(1L, id -> loads.incrementAndGet()));
        time.addAndGet(Duration.ofSeconds(1).toNanos());
        assertEquals(2, cache.get(1L, id -> loads.incrementAndGet()));
        cache.invalidate(1L);
        assertEquals(3, cache.get(1L, id -> loads.incrementAndGet()));
    }

    @Test
    void failuresAreNotCachedAndDifferentTargetsAreIsolated() {
        var cache = new MetricSnapshotCache<Long, String>();
        assertThrows(IllegalStateException.class, () -> cache.get(1L, id -> { throw new IllegalStateException("offline"); }));
        assertEquals("recovered", cache.get(1L, id -> "recovered"));
        assertEquals("other", cache.get(2L, id -> "other"));
        assertEquals("recovered", cache.get(1L, id -> "unexpected reload"));
    }

    @Test
    void concurrentMetricsShareOneTargetRead() throws Exception {
        var cache = new MetricSnapshotCache<Long, Integer>();
        var loads = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(6);
        var pending = new ArrayList<Future<Integer>>();
        try {
            for (int i = 0; i < 6; i++) pending.add(pool.submit(() -> cache.get(1L, id -> {
                loads.incrementAndGet();
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return 42;
            })));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            for (var result : pending) assertEquals(42, result.get(5, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
