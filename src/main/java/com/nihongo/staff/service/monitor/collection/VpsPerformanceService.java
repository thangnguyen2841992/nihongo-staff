package com.nihongo.staff.service.monitor.collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VpsPerformanceService {
    public record Point(double timestamp, Double value) {}
    public record Series(String objectKey, String objectName, Map<String, String> labels, String status, boolean stale, List<Point> points) {}
    public record Performance(long vpsId, String metricCode, String state, String collectionError, List<Series> objects, int scheduleSeconds, List<MonitorEventService.Event> events) {}
    private final MonitorVpsRepository servers;
    private final MonitorVpsMetricRepository assignments;
    private final MonitorObjectRepository objects;
    private final MonitorPerfValueRepository values;
    private final ObjectMapper mapper;
    private final MonitorEventService monitoringEvents;

    private MonitorVpsMetric assignment(long vpsId, String code) {
        var assignment = assignments.findByVps_VpsIdAndMetric_MetricCode(vpsId, code);
        if (assignment.isPresent()) return assignment.get();
        if (!servers.existsById(vpsId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS không tồn tại.");
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric chưa được gán cho VPS này.");
    }
    public Performance read(long vpsId, String code, Integer hours, String objectKey) {
        return read(vpsId, code, hours, objectKey, null);
    }
    public Performance read(long vpsId, String code, Integer hours, String objectKey, Integer minutes) {
        if (hours != null && (hours < 1 || hours > 168)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian phải từ 1 đến 168 giờ.");
        if (minutes != null && (minutes < 1 || minutes > 10080)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian phải từ 1 đến 10080 phút.");
        if (hours != null && minutes != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chọn một khoảng thời gian theo giờ hoặc phút.");
        Integer windowSeconds = null;
        if (minutes != null) windowSeconds = minutes * 60;
        else if (hours != null) windowSeconds = hours * 3600;
        if (windowSeconds != null && (objectKey == null || objectKey.isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn object để xem lịch sử.");
        MonitorVpsMetric link = assignment(vpsId, code);
        MonitorMetric metric = link.getMetric();
        LocalDateTime now = PerfCollectionStore.now();
        boolean fresh = link.getLastSuccessAt() != null && link.getLastSuccessAt().isAfter(now.minusSeconds(PerfCollectionStore.interval(link) * 2L + 10));
        String state = fresh && link.getLastError() == null ? "UP" : link.getLastAttemptAt() == null ? "UNKNOWN" : "DOWN";
        if (!Boolean.TRUE.equals(link.getEnabled()) || !Boolean.TRUE.equals(metric.getEnabled())) state = "PAUSED";
        List<Series> result = new ArrayList<>();
        if (!Boolean.TRUE.equals(metric.getObjectLevelYn())) {
            if (objectKey == null || objectKey.equals("vps")) result.add(series(link, null, windowSeconds, now));
        } else {
            for (MonitorObject object : objects.findByVps_VpsIdAndObjectType(vpsId, metric.getObjectType()))
                if (objectKey == null || objectKey.equals(object.getObjectId().toString())) result.add(series(link, object, windowSeconds, now));
        }
        result.sort(Comparator.comparing(Series::objectName));
        return new Performance(vpsId, code, state, link.getLastError(), result, PerfCollectionStore.interval(link),
                windowSeconds == null ? monitoringEvents.recent(vpsId, metric.getMetricId(), null) : List.of());
    }
    private Series series(MonitorVpsMetric link, MonitorObject object, Integer windowSeconds, LocalDateTime now) {
        Long objectId = object == null ? null : object.getObjectId();
        List<Point> points = new ArrayList<>();
        boolean stale = true;
        if (windowSeconds == null) {
            Optional<MonitorPerfValue> latest = values.findFirstByVpsIdAndMetricIdAndObjectIdOrderByCollectedAtDesc(link.getVps().getVpsId(), link.getMetric().getMetricId(), objectId);
            if (latest.isPresent()) {
                var value = latest.get(); points.add(new Point(value.getCollectedAt().toEpochSecond(ZoneOffset.UTC), value.getValue()));
                stale = value.getCollectedAt().isBefore(now.minusSeconds(PerfCollectionStore.interval(link) * 2L + 10)) || link.getLastError() != null;
            }
        } else {
            int step = Math.max(5, (int) Math.ceil(windowSeconds / 1000D));
            List<Point> samples;
            if (windowSeconds <= 600) {
                samples = values.findByVpsIdAndMetricIdAndObjectIdAndCollectedAtBetweenOrderByCollectedAtAsc(link.getVps().getVpsId(), link.getMetric().getMetricId(), objectId, now.minusSeconds(windowSeconds), now)
                        .stream().map(v -> new Point(v.getCollectedAt().toEpochSecond(ZoneOffset.UTC), v.getValue())).toList();
            } else {
                samples = values.history(link.getVps().getVpsId(), link.getMetric().getMetricId(), objectId, now.minusSeconds(windowSeconds), now, step)
                        .stream().map(b -> new Point(b.getTimestamp(), b.getValue())).toList();
            }
            for (var sample : samples) {
                if (!points.isEmpty() && sample.timestamp() - points.get(points.size() - 1).timestamp() > Math.max(step * 2, PerfCollectionStore.interval(link) * 2))
                    points.add(new Point(points.get(points.size() - 1).timestamp() + step, null));
                points.add(sample);
            }
        }
        Map<String, String> labels = Map.of();
        if (object != null && object.getLabelsJson() != null) {
            try { labels = mapper.readValue(object.getLabelsJson(), new TypeReference<Map<String, String>>() {}); }
            catch (Exception ignored) { /* Older objects may not yet have label metadata. */ }
        }
        return new Series(object == null ? "vps" : objectId.toString(), object == null
                ? link.getMetric().getMetricName()
                : Optional.ofNullable(object.getObjectName()).orElse(object.getObjectKey()), labels,
                object == null ? "ACTIVE" : object.getStatus().name(), stale || (object != null && object.getStatus() == ObjectStatus.OFFLINE), points);
    }
}
