package com.nihongo.staff.service.monitor.collection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class MetricCollector {
    private final PerfCollectionStore store;
    private final NodeMetricSource source;

    public void collectAssignment(long id) {
        PerfCollectionStore.Job job = null;
        try {
            job = store.claim(id);
            if (job == null) return;
            if (!"NODE_EXPORTER".equals(job.collector())) throw new IllegalStateException("Collector chưa được hỗ trợ: " + job.collector());
            var samples = source.fetch(job.host(), job.port(), job.timeout());
            var observedAt = PerfCollectionStore.now();
            store.complete(job, NodeMetricSource.readings(job.code(), samples), observedAt);
        } catch (Exception e) {
            if (job != null) {
                try {
                    String message = e.getMessage() == null ? "Thu thập thất bại." : e.getMessage();
                    if (store.fail(job, message))
                        log.warn("Metric collection failed: assignment={}, VPS={}:{}, metric={}, reason={}", id, job.host(), job.port(), job.code(), message);
                }
                catch (Exception saveError) { log.warn("Cannot save collection failure for assignment {}: {}", id, saveError.getClass().getSimpleName()); }
            } else {
                log.warn("Cannot claim metric assignment {}: {}", id, e.getClass().getSimpleName());
            }
        }
    }
}
