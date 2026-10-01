package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:perf_race;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "monitoring.prometheus-targets-file=target/prometheus-test/node_targets.json", "monitoring.prometheus-windows-targets-file=target/prometheus-test/windows_targets.json"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PerfPersistenceTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentObjectCollectionTest {
    @Autowired MetricCatalog catalog;
    @Autowired MonitorVpsRepository servers;
    @Autowired MonitorVpsMetricRepository assignments;
    @Autowired MonitorObjectRepository objects;
    @Autowired MonitorPerfBaselineRepository baselines;
    @Autowired PerfCollectionStore store;

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void receiveAndTransmitCreateOnlyOneInterfaceInSeparateConcurrentTransactions() throws Exception {
        catalog.initialize();
        MonitorVps server = new MonitorVps(); server.setIpAddress("127.0.0.1"); server.setAgentPort(9100); server.setHostname("race-test"); server = servers.save(server); catalog.bindDefaults(server);
        var links = assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(server.getVpsId());
        var receive = links.stream().filter(a -> a.getMetric().getMetricCode().equals("NETWORK_RECEIVE")).findFirst().orElseThrow();
        var transmit = links.stream().filter(a -> a.getMetric().getMetricCode().equals("NETWORK_TRANSMIT")).findFirst().orElseThrow();
        var first = store.claim(receive.getVpsMetricId()); var second = store.claim(transmit.getVpsMetricId());
        assertNotNull(first); assertNotNull(second);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var reading = List.of(new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, 100D, null));
        LocalDateTime time = PerfCollectionStore.now();
        try {
            var a = workers.submit(() -> { await(start); store.complete(first, reading, time); });
            var b = workers.submit(() -> { await(start); store.complete(second, reading, time); });
            start.countDown(); a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        assertEquals(1, objects.findByVps_VpsId(server.getVpsId()).size());
        assertEquals(2, baselines.count());
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test barrier timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
