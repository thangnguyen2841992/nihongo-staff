package com.nihongo.staff.service.monitor.collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VpsPerformanceService {
    public record Metric(long metricId, String code, String name, String unit, String objectType, int scheduleSeconds, int timeoutMs, boolean enabled) {}
    public record Config(long assignmentId, String code, Integer scheduleSeconds, int effectiveScheduleSeconds, boolean enabled, String lastError, LocalDateTime lastSuccessAt) {}
    public record Point(double timestamp, Double value) {}
    public record Series(String objectKey, String objectName, Map<String, String> labels, String status, boolean stale, List<Point> points) {}
    public record Performance(long vpsId, String metricCode, String state, String collectionError, List<Series> objects) {}
    public record Update(Integer scheduleSeconds, Integer timeoutMs, Boolean enabled) {}
    private final MonitorMetricRepository metrics;
    private final MonitorVpsRepository servers;
    private final MonitorVpsMetricRepository assignments;
    private final MonitorObjectRepository objects;
    private final MonitorPerfValueRepository values;
    private final ObjectMapper mapper;

    public List<Metric> metrics() {
        return metrics.findAll().stream().map(m -> new Metric(m.getMetricId(), m.getMetricCode(), m.getMetricName(), m.getUnit(), m.getObjectType(), Optional.ofNullable(m.getScheduleSeconds()).orElse(60), Optional.ofNullable(m.getTimeoutMs()).orElse(5000), Boolean.TRUE.equals(m.getEnabled()))).toList();
    }
    public List<Config> configs(long vpsId) {
        requireVps(vpsId);
        return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vpsId).stream().map(this::config).toList();
    }
    private Config config(MonitorVpsMetric link) { return new Config(link.getVpsMetricId(), link.getMetric().getMetricCode(), link.getScheduleSeconds(), PerfCollectionStore.interval(link), Boolean.TRUE.equals(link.getEnabled()), link.getLastError(), link.getLastSuccessAt()); }
    private void requireVps(long id) { if (!servers.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS không tồn tại."); }
    private MonitorVpsMetric assignment(long vpsId, String code) {
        requireVps(vpsId);
        return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vpsId).stream().filter(a -> a.getMetric().getMetricCode().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric chưa được gán cho VPS này."));
    }
    static void validate(Update update) {
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
        return config(link);
    }
    @Transactional
    public void updateMetric(long metricId, Update update) {
        validate(update);
        MonitorMetric metric = metrics.findById(metricId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric không tồn tại."));
        if (update.scheduleSeconds() != null) metric.setScheduleSeconds(update.scheduleSeconds());
        if (update.timeoutMs() != null) metric.setTimeoutMs(update.timeoutMs());
        if (update.enabled() != null) metric.setEnabled(update.enabled());
        for (MonitorVpsMetric link : assignments.findAll()) {
            if (link.getMetric().getMetricId().equals(metricId) && link.getScheduleSeconds() == null)
                assignments.lockById(link.getVpsMetricId()).orElseThrow().setNextCollectionAt(PerfCollectionStore.now());
        }
    }
    public Performance read(long vpsId, String code, Integer hours, String objectKey) {
        if (hours != null && (hours < 1 || hours > 168)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian phải từ 1 đến 168 giờ.");
        if (hours != null && (objectKey == null || objectKey.isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn object để xem lịch sử.");
        MonitorVpsMetric link = assignment(vpsId, code);
        MonitorMetric metric = link.getMetric();
        LocalDateTime now = PerfCollectionStore.now();
        boolean fresh = link.getLastSuccessAt() != null && link.getLastSuccessAt().isAfter(now.minusSeconds(PerfCollectionStore.interval(link) * 2L + 10));
        String state = fresh && link.getLastError() == null ? "UP" : link.getLastAttemptAt() == null ? "UNKNOWN" : "DOWN";
        if (!Boolean.TRUE.equals(link.getEnabled()) || !Boolean.TRUE.equals(metric.getEnabled())) state = "PAUSED";
        List<Series> result = new ArrayList<>();
        if (!Boolean.TRUE.equals(metric.getObjectLevelYn())) {
            if (objectKey == null || objectKey.equals("vps")) result.add(series(link, null, hours, now));
        } else {
            for (MonitorObject object : objects.findByVps_VpsIdAndObjectType(vpsId, metric.getObjectType()))
                if (objectKey == null || objectKey.equals(object.getObjectId().toString())) result.add(series(link, object, hours, now));
        }
        result.sort(Comparator.comparing(Series::objectName));
        return new Performance(vpsId, code, state, link.getLastError(), result);
    }
    private Series series(MonitorVpsMetric link, MonitorObject object, Integer hours, LocalDateTime now) {
        Long objectId = object == null ? null : object.getObjectId();
        List<Point> points = new ArrayList<>();
        boolean stale = true;
        if (hours == null) {
            Optional<MonitorPerfValue> latest = values.findFirstByVpsIdAndMetricIdAndObjectIdOrderByCollectedAtDesc(link.getVps().getVpsId(), link.getMetric().getMetricId(), objectId);
            if (latest.isPresent()) {
                var value = latest.get(); points.add(new Point(value.getCollectedAt().toEpochSecond(ZoneOffset.UTC), value.getValue()));
                stale = value.getCollectedAt().isBefore(now.minusSeconds(PerfCollectionStore.interval(link) * 2L + 10)) || link.getLastError() != null;
            }
        } else {
            int step = Math.max(5, (int) Math.ceil(hours * 3600D / 1000));
            var buckets = values.history(link.getVps().getVpsId(), link.getMetric().getMetricId(), objectId, now.minusHours(hours), now, step);
            for (var bucket : buckets) {
                if (!points.isEmpty() && bucket.getTimestamp() - points.get(points.size() - 1).timestamp() > Math.max(step * 2, PerfCollectionStore.interval(link) * 2))
                    points.add(new Point(points.get(points.size() - 1).timestamp() + step, null));
                points.add(new Point(bucket.getTimestamp(), bucket.getValue()));
            }
        }
        Map<String, String> labels = Map.of();
        if (object != null && object.getLabelsJson() != null) {
            try { labels = mapper.readValue(object.getLabelsJson(), new TypeReference<Map<String, String>>() {}); }
            catch (Exception ignored) { /* Older objects may not yet have label metadata. */ }
        }
        return new Series(object == null ? "vps" : objectId.toString(), object == null ? "Toàn VPS" : Optional.ofNullable(object.getObjectName()).orElse(object.getObjectKey()), labels,
                object == null ? "ACTIVE" : object.getStatus().name(), stale || (object != null && object.getStatus() == ObjectStatus.OFFLINE), points);
    }
}
