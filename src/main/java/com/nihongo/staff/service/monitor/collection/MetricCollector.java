package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.service.monitor.mysql.MysqlMetricSource;
import com.nihongo.staff.service.monitor.kafka.KafkaMetricSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class MetricCollector {
    private final PerfCollectionStore store;
    private final NodeMetricSource source;
    private final MysqlMetricSource mysql;
    private final KafkaMetricSource kafka;
    private final com.nihongo.staff.service.monitor.check.CheckMetricSource checks;
    private record ExporterEndpoint(long vpsId, String host, int port, int timeout) {}
    private final MetricSnapshotCache<ExporterEndpoint, java.util.List<NodeMetricSource.Sample>> snapshots = new MetricSnapshotCache<>();

    /**
     * Collects every due metric of the target that owns assignment {@code id}: one claim transaction, one read of the
     * target (the sources share it through their snapshot caches) and one save transaction for the whole group.
     */
    public void collectAssignment(long id) {
        java.util.List<PerfCollectionStore.Job> jobs;
        try {
            jobs = store.claimGroup(id);
        } catch (Exception e) {
            log.warn("Cannot claim metric assignment {}: {}", id, e.getClass().getSimpleName());
            return;
        }
        if (jobs == null || jobs.isEmpty()) return;
        var outcomes = jobs.stream().map(this::read).toList();
        if (outcomes.size() == 1) { save(outcomes.get(0)); return; }
        try {
            var changed = store.finish(outcomes);
            for (var outcome : outcomes) if (changed.contains(outcome.job().id())) logFailure(outcome.job(), outcome.failure());
        } catch (Exception e) {
            // Nothing of the group was written; leases are still ours, so save each metric on its own instead.
            log.warn("Grouped save failed for target {} ({} metrics), saving individually: {}",
                    jobs.get(0).vpsId(), outcomes.size(), e.getClass().getSimpleName());
            outcomes.forEach(this::save);
        }
    }

    private PerfCollectionStore.Outcome read(PerfCollectionStore.Job job) {
        try {
            if ("PORT_CHECK".equals(job.collector()) || "WEB_CHECK".equals(job.collector())) {
                var probe = checks.collect(job.vpsId(), job.timeout());
                return PerfCollectionStore.Outcome.checked(job, com.nihongo.staff.service.monitor.check.CheckMetricSource.readings(job.code(), probe),
                        PerfCollectionStore.now(), probe.healthy() ? null : probe.message());
            }
            if ("MYSQL_JDBC".equals(job.collector()))
                return PerfCollectionStore.Outcome.completed(job, mysql.readings(job.vpsId(), job.host(), job.port(), job.code(), job.timeout()),
                        PerfCollectionStore.now());
            if ("KAFKA_ADMIN".equals(job.collector()))
                return PerfCollectionStore.Outcome.completed(job, kafka.readings(job.vpsId(), job.code(), job.timeout()), PerfCollectionStore.now());
            if (!"NODE_EXPORTER".equals(job.collector()) && !"WINDOWS_EXPORTER".equals(job.collector()))
                throw new IllegalStateException("Collector chưa được hỗ trợ: " + job.collector());
            var endpoint = new ExporterEndpoint(job.vpsId(), job.host(), job.port(), job.timeout());
            var samples = snapshots.get(endpoint,
                    key -> java.util.List.copyOf(source.fetch(key.host(), key.port(), key.timeout())));
            ExporterType actual = NodeMetricSource.exporterType(samples);
            String reported = NodeMetricSource.hostname(samples);
            if (!LocalHost.same(job.hostname(), reported))
                throw new IllegalStateException("Exporter tại " + job.host() + ":" + job.port() + " là máy " + reported
                        + ", không phải " + job.hostname() + ". Dùng IP LAN/hostname của máy đó thay cho 127.0.0.1.");
            if (!actual.name().equals(job.collector()))
                throw new IllegalStateException("Exporter tại " + job.host() + ":" + job.port() + " đã đổi loại; cần đăng ký lại máy chủ.");
            return PerfCollectionStore.Outcome.completed(job, NodeMetricSource.readings(job.code(), samples, actual), PerfCollectionStore.now());
        } catch (Exception e) {
            return PerfCollectionStore.Outcome.failed(job, e.getMessage() == null ? "Thu thập thất bại." : e.getMessage());
        }
    }

    // Single-metric save (one transaction); a reading that cannot be stored is recorded as a collection failure.
    private void save(PerfCollectionStore.Outcome outcome) {
        var job = outcome.job();
        String failure = outcome.failure();
        if (failure == null) {
            try {
                if (outcome.check()) store.completeCheck(job, outcome.readings(), outcome.observedAt(), outcome.checkError());
                else store.complete(job, outcome.readings(), outcome.observedAt());
                return;
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Thu thập thất bại." : e.getMessage();
            }
        }
        try {
            if (store.fail(job, failure)) logFailure(job, failure);
        } catch (Exception saveError) {
            log.warn("Cannot save collection failure for assignment {}: {}", job.id(), saveError.getClass().getSimpleName());
        }
    }

    private void logFailure(PerfCollectionStore.Job job, String message) {
        log.warn("Metric collection failed: assignment={}, target={}:{}, metric={}, reason={}", job.id(), job.host(), job.port(), job.code(), message);
    }
}
