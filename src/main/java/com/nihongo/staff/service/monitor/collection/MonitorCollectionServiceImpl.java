package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.MonitorMetric;
import com.nihongo.staff.model.monitoring.MonitorObject;
import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class MonitorCollectionServiceImpl implements MonitorCollectionService {
    private final MonitorVpsMetricRepository assignments;
    private final PerfCollectionStore store;
    private final NodeMetricSource source;

    @Override public void collectMetric(Long vpsId, MonitorMetric metric) {
        assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vpsId).stream()
                .filter(link -> link.getMetric().getMetricId().equals(metric.getMetricId()))
                .findFirst().ifPresent(link -> collectAssignment(link.getVpsMetricId()));
    }
    @Override public void collectObjectMetric(Long vpsId, MonitorMetric metric) { collectMetric(vpsId, metric); }
    @Override public void collectVpsMetric(Long vpsId, MonitorMetric metric) { collectMetric(vpsId, metric); }
    // A metric is collected as one batch to discover new objects and keep counters consistent.
    @Override public void collect(Long vpsId, MonitorMetric metric, MonitorObject object) { collectMetric(vpsId, metric); }

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
