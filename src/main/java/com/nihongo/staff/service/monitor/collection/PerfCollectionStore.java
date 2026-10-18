package com.nihongo.staff.service.monitor.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PerfCollectionStore {
    public record Job(long id, String token, long vpsId, String host, int port, String code, String collector, int timeout) {}
    private final MonitorVpsMetricRepository assignments;
    private final MonitorVpsRepository servers;
    private final MonitorObjectRepository objects;
    private final PerfValueBatchWriter valueWriter;
    private final MonitorPerfBaselineRepository baselines;
    private final MonitorEventService eventRules;
    private final ObjectMapper mapper;
    private final org.springframework.context.ApplicationEventPublisher events;
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    public static int interval(MonitorVpsMetric link) {
        Integer seconds = link.getScheduleSeconds() != null ? link.getScheduleSeconds() : link.getMetric().getScheduleSeconds();
        return Math.max(5, seconds == null ? 60 : seconds);
    }
    @Transactional
    public Job claim(long id) {
        MonitorVpsMetric link = assignments.lockById(id).orElse(null);
        LocalDateTime now = now();
        if (link == null || !Boolean.TRUE.equals(link.getEnabled()) || !Boolean.TRUE.equals(link.getMetric().getEnabled()) ||
                (link.getNextCollectionAt() != null && link.getNextCollectionAt().isAfter(now)) ||
                (link.getLeaseUntil() != null && link.getLeaseUntil().isAfter(now))) return null;
        int timeout = Math.max(1000, Math.min(30000, Optional.ofNullable(link.getMetric().getTimeoutMs()).orElse(5000)));
        String token = UUID.randomUUID().toString();
        link.setLeaseToken(token); link.setLeaseUntil(now.plusSeconds(timeout / 1000 + 30));
        link.setLastAttemptAt(now); link.setNextCollectionAt(now.plusSeconds(interval(link)));
        String collector = link.getMetric().getCollectorType();
        if ("NODE_EXPORTER".equals(collector) && ExporterType.forVps(link.getVps()) == ExporterType.WINDOWS_EXPORTER)
            collector = ExporterType.WINDOWS_EXPORTER.name();
        return new Job(id, token, link.getVps().getVpsId(), link.getVps().getIpAddress(), link.getVps().getAgentPort(), link.getMetric().getMetricCode(), collector, timeout);
    }

    @Transactional
    public void discover(MonitorVps vps, List<NodeMetricSource.Sample> samples) {
        List<NodeMetricSource.Reading> discovered = new ArrayList<>();
        for (MetricCatalog.Definition definition : MetricCatalog.DEFAULTS) {
            if (ExporterType.forVps(vps) == ExporterType.WINDOWS_EXPORTER && "LOAD_1M".equals(definition.code())) continue;
            discovered.addAll(NodeMetricSource.readings(definition.code(), samples, ExporterType.forVps(vps)));
        }
        var known = loadObjects(vps.getVpsId(), discovered, null);
        var observedAt = now();
        for (var reading : discovered) if (!reading.type().equals("VPS")) upsert(vps, reading, observedAt, known);
    }

    private record ObjectKey(String type, String key) {}

    private Map<ObjectKey, MonitorObject> loadObjects(long vpsId, List<NodeMetricSource.Reading> readings, String offlineType) {
        Set<String> types = new HashSet<>();
        for (var reading : readings) if (!reading.type().equals("VPS")) types.add(reading.type());
        if (offlineType != null) types.add(offlineType);
        Map<ObjectKey, MonitorObject> known = new HashMap<>();
        if (!types.isEmpty()) for (var object : objects.findTypesForUpdate(vpsId, types))
            known.put(new ObjectKey(object.getObjectType(), object.getObjectKey()), object);
        return known;
    }

    private Map<String, MonitorPerfBaseline> loadBaselines(long assignmentId, List<NodeMetricSource.Reading> readings) {
        var keys = readings.stream().filter(reading -> reading.counter() != null).map(NodeMetricSource.Reading::key).distinct().toList();
        Map<String, MonitorPerfBaseline> known = new HashMap<>();
        for (int start = 0; start < keys.size(); start += 500)
            for (var baseline : baselines.findKeysForUpdate(assignmentId, keys.subList(start, Math.min(start + 500, keys.size()))))
                known.put(baseline.getObjectKey(), baseline);
        return known;
    }

    private MonitorObject upsert(MonitorVps vps, NodeMetricSource.Reading reading, LocalDateTime time,
                                 Map<ObjectKey, MonitorObject> known) {
        var key = new ObjectKey(reading.type(), reading.key());
        MonitorObject object = known.computeIfAbsent(key, ignored -> new MonitorObject());
        object.setVps(vps); object.setObjectType(reading.type()); object.setObjectKey(reading.key());
        String name = switch (reading.type()) {
            case "CPU" -> "CPU " + reading.labels().get("cpu");
            case "FILESYSTEM" -> reading.labels().get("mountpoint") + " (" + reading.labels().get("device") + ")";
            case "KAFKA_CONSUMER_GROUP" -> reading.labels().getOrDefault("group", reading.key());
            default -> reading.labels().getOrDefault("device", reading.key());
        };
        object.setObjectName(name.length() > 255 ? name.substring(0, 255) : name);
        try { object.setLabelsJson(mapper.writeValueAsString(reading.labels())); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize object labels", e); }
        object.setStatus(ObjectStatus.ACTIVE); object.setLastSeenAt(time);
        if (object.getFirstSeenAt() == null) object.setFirstSeenAt(time);
        return object.getObjectId() == null ? objects.save(object) : object;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void complete(Job job, List<NodeMetricSource.Reading> readings, LocalDateTime observedAt) {
        persist(job, readings, observedAt, null, false);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void completeCheck(Job job, List<NodeMetricSource.Reading> readings, LocalDateTime observedAt, String checkError) {
        persist(job, readings, observedAt, checkError, true);
    }

    private void persist(Job job, List<NodeMetricSource.Reading> readings, LocalDateTime observedAt, String checkError, boolean check) {
        MonitorVpsMetric link = assignments.lockById(job.id()).orElseThrow();
        if (!job.token().equals(link.getLeaseToken())) return;
        if (!Boolean.TRUE.equals(link.getEnabled()) || !Boolean.TRUE.equals(link.getMetric().getEnabled())) { release(link); return; }
        servers.lockById(job.vpsId()).orElseThrow();
        var knownObjects = loadObjects(job.vpsId(), readings,
                Boolean.TRUE.equals(link.getMetric().getObjectLevelYn()) ? link.getMetric().getObjectType() : null);
        var knownBaselines = loadBaselines(job.id(), readings);
        Set<String> seen = new HashSet<>();
        List<MonitorEventService.Sample> collected = new ArrayList<>();
        for (NodeMetricSource.Reading reading : readings) {
            seen.add(reading.key());
            MonitorObject object = reading.type().equals("VPS") ? null : upsert(link.getVps(), reading, observedAt, knownObjects);
            Double value = reading.value();
            if (reading.counter() != null) {
                MonitorPerfBaseline baseline = knownBaselines.computeIfAbsent(reading.key(), ignored -> new MonitorPerfBaseline());
                if (baseline.getObservedAt() != null) {
                    double seconds = Duration.between(baseline.getObservedAt(), observedAt).toNanos() / 1_000_000_000D;
                    double delta = reading.counter() - baseline.getCounterValue();
                    if (seconds > 0 && delta >= 0) {
                        if (job.code().equals("CPU_USAGE")) {
                            double idle = reading.auxiliary() - baseline.getAuxiliaryValue();
                            if (delta > 0 && idle >= 0 && idle <= delta) value = Math.max(0, Math.min(100, 100 * (1 - idle / delta)));
                        } else value = delta / seconds;
                    }
                }
                baseline.setVpsMetricId(job.id()); baseline.setObjectKey(reading.key());
                baseline.setCounterValue(reading.counter()); baseline.setAuxiliaryValue(reading.auxiliary()); baseline.setObservedAt(observedAt);
                if (baseline.getBaselineId() == null) baselines.save(baseline);
            }
            if (value != null && Double.isFinite(value)) {
                MonitorPerfValue perf = new MonitorPerfValue(); perf.setVpsId(job.vpsId()); perf.setMetricId(link.getMetric().getMetricId());
                perf.setObjectId(object == null ? null : object.getObjectId()); perf.setCollectedAt(observedAt); perf.setValue(value);
                collected.add(new MonitorEventService.Sample(perf, object == null
                        ? link.getMetric().getMetricName() : object.getObjectName()));
            }
        }
        if (Boolean.TRUE.equals(link.getMetric().getObjectLevelYn())) {
            for (MonitorObject object : knownObjects.values())
                if (Objects.equals(object.getObjectType(), link.getMetric().getObjectType()) && !seen.contains(object.getObjectKey()))
                    object.setStatus(ObjectStatus.OFFLINE);
        }
        valueWriter.write(collected.stream().map(MonitorEventService.Sample::perf).toList());
        link.setLastSuccessAt(observedAt); link.setLastError(readings.isEmpty()
                ? ("MYSQL_JDBC".equals(job.collector()) ? "MySQL chưa cung cấp metric này."
                : "KAFKA_ADMIN".equals(job.collector()) ? "Kafka chưa cung cấp metric này hoặc tài khoản thiếu quyền đọc."
                : "Exporter chưa cung cấp metric này.")
                : null);
        eventRules.evaluate(link, collected);
        if (check) link.setLastError(checkError);
        link.setConsecutiveFailures(0);
        link.getVps().setStatus(check && checkError != null ? VpsStatus.DOWN : VpsStatus.UP);
        link.getVps().setLastSeenAt(observedAt); release(link);
        events.publishEvent(new PerformanceChanged(job.vpsId(), job.code()));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean fail(Job job, String message) {
        MonitorVpsMetric link = assignments.lockById(job.id()).orElseThrow();
        if (!job.token().equals(link.getLeaseToken())) return false;
        servers.lockById(job.vpsId()).orElseThrow();
        eventRules.evaluate(link, List.of());
        String storedMessage = message.length() > 500 ? message.substring(0, 500) : message;
        boolean changed = !java.util.Objects.equals(link.getLastError(), storedMessage);
        link.setLastError(storedMessage);
        link.setConsecutiveFailures(Math.min(20, link.getConsecutiveFailures() + 1));
        release(link);
        link.setNextCollectionAt(now().plusSeconds(retryDelay(interval(link), link.getConsecutiveFailures())));
        events.publishEvent(new PerformanceChanged(job.vpsId(), job.code()));
        return changed;
    }
    static long retryDelay(int interval, int failures) {
        return Math.max(interval, Math.min(900L, Math.max(60L, interval) * (1L << Math.min(4, Math.max(0, failures - 1)))));
    }
    private void release(MonitorVpsMetric link) {
        link.setLeaseToken(null); link.setLeaseUntil(null); link.setNextCollectionAt(now().plusSeconds(interval(link)));
    }
}
