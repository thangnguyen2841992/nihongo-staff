package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:perf_write_transactions;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PerfPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerfWriteTransactionTest {
    @Autowired MetricCatalog catalog;
    @Autowired PerfCollectionStore store;
    @Autowired MonitorVpsRepository servers;
    @Autowired MonitorVpsMetricRepository assignments;
    @Autowired MonitorObjectRepository objects;
    @Autowired MonitorPerfValueRepository values;
    @Autowired MonitorPerfBaselineRepository baselines;
    @Autowired MonitorEventRepository history;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean MonitorEventService events;
    long vpsId;
    final LocalDateTime time = LocalDateTime.of(2026, 10, 17, 1, 0);

    @BeforeEach void setup() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            catalog.initialize();
            var vps = new MonitorVps(); vps.setHostname(UUID.randomUUID().toString());
            vps.setIpAddress("127.0.0.1"); vps.setAgentPort(9100);
            servers.save(vps); catalog.bindDefaults(vps); vpsId = vps.getVpsId();
        });
    }

    MonitorVpsMetric link(String code) {
        return assignments.findByVps_VpsIdAndMetric_MetricCode(vpsId, code).orElseThrow();
    }
    PerfCollectionStore.Job claim(String code) {
        long id = new TransactionTemplate(manager).execute(status -> {
            var link = link(code); link.setNextCollectionAt(PerfCollectionStore.now().minusSeconds(1));
            return link.getVpsMetricId();
        });
        return store.claim(id);
    }
    NodeMetricSource.Reading network(double counter) {
        return new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, counter, null);
    }
    void alert(String code) {
        events.save(vpsId, code, null, new MonitorEventService.Input("Test threshold", "all",
                MonitorEventRule.Operator.GTE, 0D, MonitorEventRule.Severity.WARNING, 1, true));
    }
    void together(PerfCollectionStore.Job first, NodeMetricSource.Reading firstReading,
                  PerfCollectionStore.Job second, NodeMetricSource.Reading secondReading, LocalDateTime at) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<?>> pending = new ArrayList<>();
            var jobs = List.of(first, second); var readings = List.of(firstReading, secondReading);
            for (int i = 0; i < jobs.size(); i++) {
                final int index = i;
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    try { if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                    store.complete(jobs.get(index), List.of(readings.get(index)), at);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var pendingJob : pending) pendingJob.get(15, TimeUnit.SECONDS);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void differentMetricsShareOneObjectAndKeepIndependentBaselinesUnderConcurrentCollection() throws Exception {
        together(claim("NETWORK_RECEIVE"), network(100), claim("NETWORK_TRANSMIT"), network(500), time);
        together(claim("NETWORK_RECEIVE"), network(300), claim("NETWORK_TRANSMIT"), network(900), time.plusSeconds(10));
        assertEquals(1, objects.findByVps_VpsId(vpsId).size());
        var rows = values.findAll().stream().filter(value -> value.getVpsId() == vpsId).toList();
        assertEquals(2, rows.size());
        long receiveId = link("NETWORK_RECEIVE").getMetric().getMetricId();
        assertEquals(20D, rows.stream().filter(value -> value.getMetricId() == receiveId).findFirst().orElseThrow().getValue());
        assertEquals(40D, rows.stream().filter(value -> value.getMetricId() != receiveId).findFirst().orElseThrow().getValue());
    }

    @Test void competingWorkersCannotWriteTheSameLeaseOrAlertTwice() throws Exception {
        alert("MEMORY_USAGE");
        var job = claim("MEMORY_USAGE");
        var reading = new NodeMetricSource.Reading("VPS", "vps", Map.of(), 70D, null, null);
        together(job, reading, job, reading, time);
        assertEquals(1, values.findAll().stream().filter(value -> value.getVpsId() == vpsId).count());
        assertEquals(1, history.findAll().stream().filter(event -> event.getVpsId() == vpsId).count());
        assertNull(link("MEMORY_USAGE").getLeaseToken());
    }

    @Test void alertFailureRollsBackJdbcSamplesAndJpaBaselineObjectsAndEventsTogether() {
        store.complete(claim("NETWORK_RECEIVE"), List.of(network(100)), time);
        alert("NETWORK_RECEIVE");
        var job = claim("NETWORK_RECEIVE");
        var evaluation = org.springframework.test.util.AopTestUtils.<MonitorEventService>getUltimateTargetObject(events);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("Simulated failure after alerts"); })
                .when(evaluation).evaluate(any(), anyList());
        var newlyDiscovered = new NodeMetricSource.Reading("NETWORK", "eth1", Map.of("device", "eth1"), null, 500D, null);
        assertThrows(IllegalStateException.class, () -> store.complete(job, List.of(network(300), newlyDiscovered), time.plusSeconds(10)));
        assertEquals(0, values.findAll().stream().filter(value -> value.getVpsId() == vpsId).count());
        assertEquals(0, history.findAll().stream().filter(event -> event.getVpsId() == vpsId).count());
        assertEquals(100D, baselines.findByVpsMetricIdAndObjectKey(job.id(), "eth0").orElseThrow().getCounterValue());
        assertTrue(baselines.findByVpsMetricIdAndObjectKey(job.id(), "eth1").isEmpty());
        var storedObjects = objects.findByVps_VpsId(vpsId);
        assertEquals(1, storedObjects.size());
        assertEquals(time, storedObjects.get(0).getLastSeenAt());
        assertEquals(time, link("NETWORK_RECEIVE").getLastSuccessAt());
        assertEquals(job.token(), link("NETWORK_RECEIVE").getLeaseToken());
    }
}
