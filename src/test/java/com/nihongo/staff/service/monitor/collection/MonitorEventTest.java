package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:monitor_events;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "monitoring.prometheus-host=localhost", "monitoring.prometheus-port=22", "monitoring.prometheus-ssh-username=test", "monitoring.prometheus-ssh-password=test", "monitoring.prometheus-targets-file=/tmp/targets.json"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PerfPersistenceTest.Config.class)
class MonitorEventTest {
    @Autowired MetricCatalog catalog;
    @Autowired PerfCollectionStore store;
    @Autowired MonitorEventService events;
    @Autowired MonitorVpsRepository servers;
    @Autowired MonitorVpsMetricRepository assignments;
    @Autowired MonitorObjectRepository objects;
    @Autowired MonitorEventRepository history;
    @Autowired VpsPerformanceService performance;
    @Autowired EntityManager entityManager;
    long vpsId;
    LocalDateTime time;
    @BeforeEach void setup() {
        catalog.initialize();
        var vps = new MonitorVps(); vps.setHostname("event-test"); vps.setIpAddress("127.0.0.1"); vps.setAgentPort(9100);
        servers.save(vps); catalog.bindDefaults(vps); vpsId = vps.getVpsId(); time = PerfCollectionStore.now();
    }
    MonitorVpsMetric link(String code) { return assignments.findByVps_VpsIdAndMetric_MetricCode(vpsId, code).orElseThrow(); }
    PerfCollectionStore.Job claim(String code) { var link = link(code); link.setNextCollectionAt(PerfCollectionStore.now().minusSeconds(1)); return store.claim(link.getVpsMetricId()); }
    MonitorEventService.Input input(String target, MonitorEventRule.Operator op, double threshold, int samples, boolean enabled) {
        return new MonitorEventService.Input("Quá tải", target, op, threshold, MonitorEventRule.Severity.CRITICAL, samples, enabled);
    }
    void memory(double value, int seconds) {
        store.complete(claim("MEMORY_USAGE"), List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), value, null, null)), time.plusSeconds(seconds));
    }
    NodeMetricSource.Reading disk(String mount, double value) {
        return new NodeMetricSource.Reading("FILESYSTEM", mount, Map.of("device", "/dev/sda", "mountpoint", mount), value, null, null);
    }
    void disks(int seconds, NodeMetricSource.Reading... readings) { store.complete(claim("DISK_USAGE"), List.of(readings), time.plusSeconds(seconds)); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(MonitorEventRule.Severity.class)
    void allFourSeverityLevelsRoundTripThroughRulesAlertsAndRecovery(MonitorEventRule.Severity severity) {
        events.save(vpsId, "MEMORY_USAGE", null, new MonitorEventService.Input("Memory high", "all",
                MonitorEventRule.Operator.GTE, 80D, severity, 1, true));
        memory(90, 0); memory(50, 60);
        entityManager.flush(); entityManager.clear();
        assertEquals(severity, events.rules(vpsId, "MEMORY_USAGE").get(0).severity());
        var recorded = events.events(vpsId, "MEMORY_USAGE", null);
        assertEquals(2, recorded.size());
        assertTrue(recorded.stream().allMatch(event -> event.severity() == severity));
        assertEquals(List.of("MINOR", "WARNING", "CRITICAL", "FATAL"), Arrays.stream(MonitorEventRule.Severity.values()).map(Enum::name).toList());
    }

    @Test void legacyInfoRulesAndEventHistoryAreReadAsMinorAndNewWritesUseMinor() {
        var rule = events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 1, true));
        memory(90, 0); entityManager.flush();
        entityManager.createNativeQuery("update monitor_event_rule set severity = 'INFO' where rule_id = :id").setParameter("id", rule.ruleId()).executeUpdate();
        entityManager.createNativeQuery("update monitor_event set severity = 'INFO' where vps_id = :id").setParameter("id", vpsId).executeUpdate();
        entityManager.clear();
        assertEquals(MonitorEventRule.Severity.MINOR, events.rules(vpsId, "MEMORY_USAGE").get(0).severity());
        assertEquals(MonitorEventRule.Severity.MINOR, events.events(vpsId, "MEMORY_USAGE", null).get(0).severity());
        memory(50, 60);
        var recovery = events.events(vpsId, "MEMORY_USAGE", null).get(0);
        assertEquals(MonitorEvent.Kind.RECOVERY, recovery.kind());
        assertEquals("MINOR", entityManager.createNativeQuery("select severity from monitor_event where event_id = :id").setParameter("id", recovery.eventId()).getSingleResult());
        events.save(vpsId, "MEMORY_USAGE", rule.ruleId(), new MonitorEventService.Input("Memory high", "all",
                MonitorEventRule.Operator.GTE, 80D, MonitorEventRule.Severity.MINOR, 1, true));
        entityManager.flush();
        assertEquals("MINOR", entityManager.createNativeQuery("select severity from monitor_event_rule where rule_id = :id").setParameter("id", rule.ruleId()).getSingleResult());
    }

    @Test void consecutiveBreachDeduplicatesPersistsStateAndLinksRecovery() {
        events.save(vpsId, "MEMORY_USAGE", null, input("vps", MonitorEventRule.Operator.GTE, 80, 2, true));
        memory(80, 0); assertEquals(0, history.count());
        memory(90, 60); assertEquals(1, history.count());
        entityManager.flush(); entityManager.clear(); // No in-memory state is needed across collectors/restarts.
        memory(95, 120); assertEquals(1, history.count());
        memory(79, 180); memory(60, 240);
        var recorded = events.events(vpsId, "MEMORY_USAGE", null);
        assertEquals(2, recorded.size()); assertEquals(MonitorEvent.Kind.RECOVERY, recorded.get(0).kind());
        assertEquals(recorded.get(1).eventId(), recorded.get(0).openedEventId());
        assertEquals(90D, recorded.get(1).value()); assertEquals(80D, recorded.get(1).threshold());
        memory(90, 300); memory(90, 360); assertEquals(3, history.count());
        assertEquals(1, events.rules(vpsId, "MEMORY_USAGE").get(0).activeObjects());
        assertEquals(3, performance.read(vpsId, "MEMORY_USAGE", null, null).events().size());
    }
    @Test void allObjectRuleAutomaticallyAppliesToNewObjectsIndependently() {
        events.save(vpsId, "DISK_USAGE", null, input("all", MonitorEventRule.Operator.GT, 80, 2, true));
        disks(0, disk("/", 90), disk("/data", 50));
        disks(60, disk("/", 90), disk("/data", 90));
        assertEquals(1, history.count());
        disks(120, disk("/", 50), disk("/data", 90), disk("/new", 99));
        assertEquals(3, history.count());
        disks(180, disk("/", 50), disk("/data", 90), disk("/new", 99));
        assertEquals(4, history.count());
        assertEquals(2, events.rules(vpsId, "DISK_USAGE").get(0).activeObjects());
        assertEquals(3, events.events(vpsId, "DISK_USAGE", null).stream().filter(e -> e.kind() == MonitorEvent.Kind.ALERT).map(MonitorEventService.Event::objectKey).distinct().count());
    }
    @Test void specificObjectIsIsolatedFromOtherObjectsAndMetrics() {
        disks(0, disk("/", 20), disk("/data", 20));
        var root = objects.findByVps_VpsId(vpsId).stream().filter(o -> o.getObjectKey().equals("/")).findFirst().orElseThrow();
        events.save(vpsId, "DISK_USAGE", null, input(root.getObjectId().toString(), MonitorEventRule.Operator.GTE, 80, 1, true));
        disks(60, disk("/", 79), disk("/data", 99)); memory(99, 60); assertEquals(0, history.count());
        disks(120, disk("/", 80), disk("/data", 99)); assertEquals(1, history.count());
        assertEquals(root.getObjectId().toString(), events.events(vpsId, "DISK_USAGE", null).get(0).objectKey());
    }
    @Test void failureMissingAndLongGapsBreakPendingStreakWithoutFakeRecovery() {
        events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 2, true));
        memory(90, 0); store.fail(claim("MEMORY_USAGE"), "timeout"); memory(90, 60); assertEquals(0, history.count());
        store.complete(claim("MEMORY_USAGE"), List.of(), time.plusSeconds(90)); memory(90, 120); assertEquals(0, history.count());
        memory(90, 360); assertEquals(0, history.count()); memory(90, 420); assertEquals(1, history.count());
        store.fail(claim("MEMORY_USAGE"), "timeout");
        store.complete(claim("MEMORY_USAGE"), List.of(), time.plusSeconds(480));
        assertEquals(1, history.count()); assertEquals(1, events.rules(vpsId, "MEMORY_USAGE").get(0).activeObjects());
        memory(50, 540); assertEquals(2, history.count());
    }
    @Test void counterWarmupResetAndNonFiniteValuesNeverGenerateEvents() {
        events.save(vpsId, "CPU_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 0, 1, true));
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 100D, 50D)), time);
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 10D, 5D)), time.plusSeconds(30));
        assertEquals(0, history.count());
        events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 0, 1, true));
        memory(Double.NaN, 0); memory(Double.POSITIVE_INFINITY, 60); assertEquals(0, history.count());
    }
    @Test void editingDisablingAndDeletingKeepImmutableHistoryAndResetState() {
        var rule = events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 1, true));
        memory(90, 0);
        events.save(vpsId, "MEMORY_USAGE", rule.ruleId(), input("all", MonitorEventRule.Operator.GTE, 95, 1, false));
        memory(99, 60); assertEquals(1, history.count()); assertEquals(0, events.rules(vpsId, "MEMORY_USAGE").get(0).activeObjects());
        events.save(vpsId, "MEMORY_USAGE", rule.ruleId(), input("all", MonitorEventRule.Operator.GTE, 95, 1, true));
        memory(99, 120); assertEquals(2, history.count());
        events.delete(vpsId, "MEMORY_USAGE", rule.ruleId()); memory(99, 180);
        assertEquals(2, history.count()); assertTrue(events.rules(vpsId, "MEMORY_USAGE").isEmpty());
        var recorded = events.events(vpsId, "MEMORY_USAGE", null);
        assertEquals(95, recorded.get(0).threshold()); assertEquals(80, recorded.get(1).threshold());
    }
    @Test void duplicateAndOutOfOrderSamplesDoNotCountTwice() {
        events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 2, true));
        memory(90, 0); memory(90, 0); memory(90, -60); assertEquals(0, history.count());
        memory(90, 60); assertEquals(1, history.count());
    }
    @Test void validationRejectsCrossMetricCrossVpsAndInvalidThresholds() {
        disks(0, disk("/", 20));
        var disk = objects.findByVps_VpsId(vpsId).get(0);
        assertThrows(ResponseStatusException.class, () -> events.save(vpsId, "CPU_USAGE", null, input(disk.getObjectId().toString(), MonitorEventRule.Operator.GTE, 80, 1, true)));
        assertThrows(ResponseStatusException.class, () -> events.save(vpsId, "MEMORY_USAGE", null, input(disk.getObjectId().toString(), MonitorEventRule.Operator.GTE, 80, 1, true)));
        assertThrows(ResponseStatusException.class, () -> events.save(vpsId, "CPU_USAGE", null, input("vps", MonitorEventRule.Operator.GTE, 80, 1, true)));
        assertThrows(ResponseStatusException.class, () -> events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, Double.NaN, 1, true)));
        assertThrows(ResponseStatusException.class, () -> events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 0, true)));
        var rule = events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 1, true));
        assertThrows(ResponseStatusException.class, () -> events.delete(vpsId, "CPU_USAGE", rule.ruleId()));
        var other = new MonitorVps(); other.setHostname("other"); other.setIpAddress("127.0.0.2"); other.setAgentPort(9100); servers.save(other); catalog.bindDefaults(other);
        assertThrows(ResponseStatusException.class, () -> events.save(other.getVpsId(), "DISK_USAGE", null, input(disk.getObjectId().toString(), MonitorEventRule.Operator.GTE, 80, 1, true)));
        assertThrows(ResponseStatusException.class, () -> events.save(other.getVpsId(), "MEMORY_USAGE", rule.ruleId(), input("all", MonitorEventRule.Operator.GTE, 80, 1, true)));
    }
    @Test void historyUsesStableCursorAndMetricIsolation() {
        events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.GTE, 80, 1, true));
        for (int i = 0; i < 54; i++) memory(i % 2 == 0 ? 90 : 50, i * 60);
        var first = events.events(vpsId, "MEMORY_USAGE", null); assertEquals(50, first.size());
        var older = events.events(vpsId, "MEMORY_USAGE", first.get(49).eventId()); assertEquals(4, older.size());
        assertTrue(older.stream().allMatch(e -> e.eventId() < first.get(49).eventId()));
        assertTrue(events.events(vpsId, "CPU_USAGE", null).isEmpty());
    }
    @Test void allComparisonBoundariesAreExplicit() {
        assertFalse(MonitorEventRule.Operator.GT.matches(80, 80)); assertTrue(MonitorEventRule.Operator.GTE.matches(80, 80));
        assertFalse(MonitorEventRule.Operator.LT.matches(80, 80)); assertTrue(MonitorEventRule.Operator.LTE.matches(80, 80));
        events.save(vpsId, "MEMORY_USAGE", null, input("all", MonitorEventRule.Operator.LT, 80, 1, true));
        memory(80, 0); assertEquals(0, history.count()); memory(79, 60); memory(80, 120);
        assertEquals(List.of(MonitorEvent.Kind.RECOVERY, MonitorEvent.Kind.ALERT), events.events(vpsId, "MEMORY_USAGE", null).stream().map(MonitorEventService.Event::kind).toList());
    }
}
