package com.nihongo.staff.service.monitor.metric;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.collection.PerfCollectionStore;
import com.nihongo.staff.service.monitor.collection.PerformanceChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VpsMetricConfigService {
    public record Metric(long metricId, String code, String name, String unit, String objectType, int scheduleSeconds, int timeoutMs, boolean enabled) {}
    public record Config(long assignmentId, String code, Integer scheduleSeconds, int effectiveScheduleSeconds, boolean enabled, String lastError, LocalDateTime lastSuccessAt) {}
    public record Update(Integer scheduleSeconds, Integer timeoutMs, Boolean enabled) {}
    private final MonitorMetricRepository metrics;
    private final MonitorVpsRepository servers;
    private final MonitorVpsMetricRepository assignments;
    private final org.springframework.context.ApplicationEventPublisher events;

    public List<Metric> metrics() {
        return metrics.findAll().stream().map(m -> new Metric(
                m.getMetricId(), m.getMetricCode(), m.getMetricName(), m.getUnit(), m.getObjectType(),
                Optional.ofNullable(m.getScheduleSeconds()).orElse(60),
                Optional.ofNullable(m.getTimeoutMs()).orElse(5000), Boolean.TRUE.equals(m.getEnabled())))
                .toList();
    }
    public List<Config> configs(long vpsId) {
        requireVps(vpsId);
        return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vpsId).stream().map(this::config).toList();
    }
    private Config config(MonitorVpsMetric link) {
        return new Config(link.getVpsMetricId(), link.getMetric().getMetricCode(), link.getScheduleSeconds(),
                PerfCollectionStore.interval(link), Boolean.TRUE.equals(link.getEnabled()),
                link.getLastError(), link.getLastSuccessAt());
    }

    private void requireVps(long id) {
        if (!servers.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS không tồn tại.");
    }
    private MonitorVpsMetric assignment(long vpsId, String code) {
        requireVps(vpsId);
        return assignments.findByVps_VpsIdAndMetric_MetricCode(vpsId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric chưa được gán cho VPS này."));
    }
    public static void validate(Update update) {
        if (update.scheduleSeconds() != null && (update.scheduleSeconds() < 5 || update.scheduleSeconds() > 86400))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chu kỳ thu thập phải từ 5 đến 86400 giây.");
        if (update.timeoutMs() != null && (update.timeoutMs() < 1000 || update.timeoutMs() > 30000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeout phải từ 1000 đến 30000 ms.");
    }
    @Transactional
    public Config updateVps(long vpsId, String code, Update update) {
        validate(update);
        MonitorVpsMetric found = assignment(vpsId, code);
        MonitorVpsMetric link = assignments.lockById(found.getVpsMetricId()).orElseThrow();
        link.setScheduleSeconds(update.scheduleSeconds());
        if (update.enabled() != null) link.setEnabled(update.enabled());
        link.setNextCollectionAt(PerfCollectionStore.now());
        events.publishEvent(new PerformanceChanged(vpsId, code));
        return config(link);
    }
    @Transactional
    public void updateMetric(long metricId, Update update) {
        validate(update);
        MonitorMetric metric = metrics.findById(metricId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric không tồn tại."));
        if (update.scheduleSeconds() != null) metric.setScheduleSeconds(update.scheduleSeconds());
        if (update.timeoutMs() != null) metric.setTimeoutMs(update.timeoutMs());
        if (update.enabled() != null) metric.setEnabled(update.enabled());
        for (MonitorVpsMetric link : assignments.findByMetric_MetricId(metricId)) {
            if (link.getScheduleSeconds() == null) {
                assignments.lockById(link.getVpsMetricId()).orElseThrow().setNextCollectionAt(PerfCollectionStore.now());
            }
            events.publishEvent(new PerformanceChanged(link.getVps().getVpsId(), metric.getMetricCode()));
        }
    }
}
