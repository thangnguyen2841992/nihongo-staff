package com.nihongo.staff.service.monitor.collection;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.function.Function;

/** Coalesces concurrent reads for a target without retaining targets or locks indefinitely. */
public final class MetricSnapshotCache<K, V> {
    private final Cache<K, V> snapshots;

    public MetricSnapshotCache() {
        this(Ticker.systemTicker());
    }

    MetricSnapshotCache(Ticker ticker) {
        snapshots = Caffeine.newBuilder().maximumSize(1024)
                .expireAfterWrite(Duration.ofSeconds(3)).ticker(ticker).build();
    }

    public V get(K key, Function<? super K, ? extends V> loader) {
        return snapshots.get(key, loader);
    }

    public void invalidate(K key) {
        snapshots.invalidate(key);
    }
}
