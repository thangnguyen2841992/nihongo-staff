package com.nihongo.staff.service.monitor.event;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.collection.PerfCollectionStore;
import com.nihongo.staff.service.monitor.collection.PerformanceChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class MonitorEventService {
    public record Input(String name, String objectKey, MonitorEventRule.Operator operator, Double threshold,
                        MonitorEventRule.Severity severity, Integer consecutiveSamples, Boolean enabled) {}
    public record Rule(long ruleId, String name, String objectKey, MonitorEventRule.Operator operator,
                       double threshold, MonitorEventRule.Severity severity, int consecutiveSamples, boolean enabled, long activeObjects) {}
    public record Event(long eventId, long ruleId, String ruleName, String objectKey, String objectName,
                        MonitorEvent.Kind kind, MonitorEventRule.Severity severity, MonitorEventRule.Operator operator,
                        double threshold, double value, double timestamp, Long openedEventId,
                        long metricId, String metricCode, String metricName, String unit) {}
    public record Page(List<Event> events, Long nextCursor) {}
    public record Sample(MonitorPerfValue perf, String objectName) {}
    private final MonitorVpsRepository servers;
    private final MonitorVpsMetricRepository assignments;
    private final MonitorObjectRepository objects;
    private final MonitorEventRuleRepository rules;
    private final MonitorEventStateRepository states;
    private final MonitorEventRepository history;
    private final MonitorMetricRepository metrics;
    private final ApplicationEventPublisher publisher;

    private MonitorVpsMetric assignment(long vpsId, String code) {
        return assignments.findByVps_VpsIdAndMetric_MetricCode(vpsId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS hoặc metric không tồn tại."));
    }
    private void lockVps(long vpsId) {
        servers.lockById(vpsId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS không tồn tại."));
    }
    public List<Rule> rules(long vpsId, String code) {
        var link = assignment(vpsId, code);
        var found = rules.findByVpsIdAndMetricIdOrderByRuleIdAsc(vpsId, link.getMetric().getMetricId());
        var active = found.isEmpty() ? List.<MonitorEventState>of() : states.findByRuleIdIn(found.stream().map(MonitorEventRule::getRuleId).toList());
        Map<Long, Long> activeByRule = new HashMap<>();
        for (var state : active) {
            if (state.isActive()) activeByRule.merge(state.getRuleId(), 1L, Long::sum);
        }
        return found.stream().map(r -> dto(r, activeByRule.getOrDefault(r.getRuleId(), 0L))).toList();
    }
    public List<Event> events(long vpsId, String code, Long beforeId) {
        if (beforeId != null && beforeId < 1) throw bad("Mốc event không hợp lệ.");
        return recent(vpsId, assignment(vpsId, code).getMetric().getMetricId(), beforeId);
    }
    public List<Event> recent(long vpsId, long metricId, Long beforeId) {
        return describe(history.history(vpsId, metricId, beforeId, PageRequest.of(0, 50)));
    }
    public Page search(long vpsId, String code, Instant from, Instant to, MonitorEventRule.Severity severity, Long beforeId) {
        if (!servers.existsById(vpsId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VPS không tồn tại.");
        if ((from == null) != (to == null) || (from != null && from.isAfter(to))) throw bad("Chọn đầy đủ mốc bắt đầu và kết thúc; bắt đầu không được sau kết thúc.");
        if (beforeId != null && beforeId < 1) throw bad("Mốc event không hợp lệ.");
        Long metricId = code == null || code.isBlank() ? null : metrics.findByMetricCode(code)
                .orElseThrow(() -> bad("Metric không tồn tại.")).getMetricId();
        var cursor = beforeId == null ? null : history.findById(beforeId).filter(e -> e.getVpsId() == vpsId)
                .orElseThrow(() -> bad("Mốc event không thuộc VPS này."));
        var rows = history.search(vpsId, metricId, from == null ? null : LocalDateTime.ofInstant(from, ZoneOffset.UTC),
                to == null ? null : LocalDateTime.ofInstant(to, ZoneOffset.UTC), severity == null ? null : severity.name(),
                cursor == null ? null : cursor.getCollectedAt(), beforeId, PageRequest.of(0, 51));
        boolean more = rows.size() > 50;
        var page = more ? rows.subList(0, 50) : rows;
        return new Page(describe(page), more ? page.get(49).getEventId() : null);
    }
    public List<Event> committed(long vpsId, List<Long> ids) {
        return describe(history.findAllById(ids).stream().filter(e -> e.getVpsId() == vpsId)
                .sorted(Comparator.comparing(MonitorEvent::getEventId).reversed()).toList());
    }
    private List<Event> describe(List<MonitorEvent> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, MonitorMetric> catalog = new HashMap<>();
        metrics.findAllById(rows.stream().map(MonitorEvent::getMetricId).distinct().toList()).forEach(m -> catalog.put(m.getMetricId(), m));
        return rows.stream().map(e -> dto(e, catalog.get(e.getMetricId()))).toList();
    }
    private static Rule dto(MonitorEventRule r, long active) {
        return new Rule(r.getRuleId(), r.getName(), r.getObjectKey(), r.getOperator(), r.getThreshold(), r.getSeverity(), r.getConsecutiveSamples(), r.getEnabled(), active);
    }
    private static Event dto(MonitorEvent e, MonitorMetric metric) {
        String objectName = e.getObjectName();
        if (metric != null && !Boolean.TRUE.equals(metric.getObjectLevelYn()) && "vps".equals(e.getObjectKey())
                && ("Toàn VPS".equals(objectName) || "Toàn MySQL".equals(objectName)))
            objectName = metric.getMetricName();
        return new Event(e.getEventId(), e.getRuleId(), e.getRuleName(), e.getObjectKey(), objectName, e.getKind(), e.getSeverity(), e.getOperator(), e.getThreshold(), e.getPerfValue(), e.getCollectedAt().toEpochSecond(ZoneOffset.UTC) + e.getCollectedAt().getNano() / 1_000_000_000D, e.getOpenedEventId(),
                e.getMetricId(), metric == null ? "METRIC_" + e.getMetricId() : metric.getMetricCode(),
                metric == null ? "Metric #" + e.getMetricId() : metric.getMetricName(),
                metric == null || metric.getUnit() == null ? "" : metric.getUnit());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Rule save(long vpsId, String code, Long ruleId, Input input) {
        lockVps(vpsId);
        var metric = assignment(vpsId, code).getMetric();
        validate(vpsId, metric, input);
        var rule = ruleId == null ? new MonitorEventRule() : owned(vpsId, metric.getMetricId(), ruleId);
        if (ruleId == null && rules.countByVpsIdAndMetricId(vpsId, metric.getMetricId()) >= 100) throw bad("Mỗi metric trên VPS hỗ trợ tối đa 100 rule.");
        rule.setVpsId(vpsId); rule.setMetricId(metric.getMetricId()); rule.setName(input.name().trim());
        rule.setObjectKey(input.objectKey()); rule.setOperator(input.operator()); rule.setThreshold(input.threshold());
        rule.setSeverity(input.severity()); rule.setConsecutiveSamples(input.consecutiveSamples()); rule.setEnabled(input.enabled());
        if (ruleId != null) states.deleteByRuleId(ruleId);
        rules.save(rule);
        publisher.publishEvent(new PerformanceChanged(vpsId, code));
        return dto(rule, 0);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long vpsId, String code, long ruleId) {
        lockVps(vpsId);
        var rule = owned(vpsId, assignment(vpsId, code).getMetric().getMetricId(), ruleId);
        states.deleteByRuleId(ruleId); rules.delete(rule);
        publisher.publishEvent(new PerformanceChanged(vpsId, code));
    }
    private MonitorEventRule owned(long vpsId, long metricId, long id) {
        return rules.findById(id).filter(r -> r.getVpsId() == vpsId && r.getMetricId() == metricId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule không thuộc VPS/metric này."));
    }
    private void validate(long vpsId, MonitorMetric metric, Input input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().trim().length() > 100
                || input.operator() == null || input.threshold() == null || !Double.isFinite(input.threshold())
                || input.severity() == null || input.enabled() == null || input.consecutiveSamples() == null
                || input.consecutiveSamples() < 1 || input.consecutiveSamples() > 100) throw bad("Nhập tên (1–100 ký tự), ngưỡng hợp lệ và số mẫu liên tiếp từ 1 đến 100.");
        if ("all".equals(input.objectKey())) return;
        if (!Boolean.TRUE.equals(metric.getObjectLevelYn())) {
            if (!"vps".equals(input.objectKey())) throw bad("Metric không có object riêng chỉ nhận chính metric hoặc tất cả object.");
            return;
        }
        Long objectId;
        try { objectId = Long.valueOf(input.objectKey()); }
        catch (RuntimeException e) { throw bad("Object không hợp lệ."); }
        var object = objects.findById(objectId).orElseThrow(() -> bad("Object không tồn tại."));
        if (!object.getObjectId().toString().equals(input.objectKey()) || object.getVps().getVpsId() != vpsId
                || !Objects.equals(object.getObjectType(), metric.getObjectType())) throw bad("Object không thuộc VPS/metric này.");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    // Caller holds the VPS mutex. Rules, states, events and perf commit atomically.
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(MonitorVpsMetric link, List<Sample> samples) {
        var enabled = rules.findByVpsIdAndMetricIdAndEnabledTrue(link.getVps().getVpsId(), link.getMetric().getMetricId());
        if (enabled.isEmpty()) return;
        var persisted = states.findByRuleIdIn(enabled.stream().map(MonitorEventRule::getRuleId).toList());
        Map<String, MonitorEventState> byKey = new HashMap<>();
        for (var s : persisted) byKey.put(s.getRuleId() + ":" + s.getObjectKey(), s);
        Set<String> observed = new HashSet<>();
        List<Long> created = new ArrayList<>();
        for (var rule : enabled) for (var sample : samples) {
            var perf = sample.perf();
            String objectKey = perf.getObjectId() == null ? "vps" : perf.getObjectId().toString();
            if (!"all".equals(rule.getObjectKey()) && !rule.getObjectKey().equals(objectKey)) continue;
            String key = rule.getRuleId() + ":" + objectKey;
            observed.add(key);
            var state = byKey.computeIfAbsent(key, k -> {
                var s = new MonitorEventState(); s.setRuleId(rule.getRuleId()); s.setObjectKey(objectKey); return s;
            });
            if (state.getLastObservedAt() != null) {
                if (!perf.getCollectedAt().isAfter(state.getLastObservedAt())) continue;
                if (perf.getCollectedAt().isAfter(state.getLastObservedAt().plusSeconds(PerfCollectionStore.interval(link) * 2L + 10))) state.setBreaches(0);
            }
            state.setLastObservedAt(perf.getCollectedAt());
            if (rule.getOperator().matches(perf.getValue(), rule.getThreshold())) {
                state.setBreaches(Math.min(rule.getConsecutiveSamples(), state.getBreaches() + 1));
                // Each accepted collection can produce a new alert once the streak qualifies.
                if (state.getBreaches() >= rule.getConsecutiveSamples()) {
                    state.setActive(true);
                    var event = emit(rule, sample, objectKey, MonitorEvent.Kind.ALERT, null);
                    state.setOpenedEventId(event.getEventId()); created.add(event.getEventId());
                }
            } else {
                state.setBreaches(0);
                if (state.isActive()) {
                    created.add(emit(rule, sample, objectKey, MonitorEvent.Kind.RECOVERY, state.getOpenedEventId()).getEventId());
                    state.setActive(false); state.setOpenedEventId(null);
                }
            }
            states.save(state);
        }
        // Missing/reset/failed samples break pending streaks, but never imply recovery.
        for (var state : persisted) if (!observed.contains(state.getRuleId() + ":" + state.getObjectKey())) state.setBreaches(0);
        if (!created.isEmpty()) publisher.publishEvent(new MonitorEventsChanged(link.getVps().getVpsId(), List.copyOf(created)));
    }
    private MonitorEvent emit(MonitorEventRule rule, Sample sample, String key, MonitorEvent.Kind kind, Long openedId) {
        var event = new MonitorEvent();
        event.setRuleId(rule.getRuleId()); event.setVpsId(rule.getVpsId()); event.setMetricId(rule.getMetricId());
        event.setRuleName(rule.getName()); event.setObjectKey(key); event.setObjectName(sample.objectName());
        event.setKind(kind); event.setSeverity(rule.getSeverity()); event.setOperator(rule.getOperator());
        event.setThreshold(rule.getThreshold()); event.setPerfValue(sample.perf().getValue()); event.setCollectedAt(sample.perf().getCollectedAt());
        event.setOpenedEventId(openedId);
        return history.save(event);
    }
}
