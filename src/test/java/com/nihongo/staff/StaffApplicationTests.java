package com.nihongo.staff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import com.nihongo.staff.service.monitor.vps.MonitorVpsServiceImplI;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:staff_startup;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "monitoring.collection.enabled=false", "MONITORING_PROMETHEUS_SSH_PASSWORD=",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
})
class StaffApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.messaging.simp.SimpMessagingTemplate messaging;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.nihongo.staff.repository.MonitorVpsRepository servers;
    @Autowired com.nihongo.staff.repository.MonitorVpsMetricRepository assignments;
    @Autowired com.nihongo.staff.service.monitor.collection.MetricCatalog catalog;
    @Autowired com.nihongo.staff.service.monitor.collection.PerfCollectionStore store;

    long assignment() {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            var vps = new com.nihongo.staff.model.monitoring.MonitorVps();
            vps.setHostname(java.util.UUID.randomUUID().toString()); vps.setIpAddress("127.0.0.1"); vps.setAgentPort(9100);
            servers.save(vps); catalog.bindDefaults(vps);
            return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vps.getVpsId()).stream()
                    .filter(a -> a.getMetric().getMetricCode().equals("MEMORY_USAGE")).findFirst().orElseThrow().getVpsMetricId();
        });
    }
    @Test void socketReceivesPersistedSnapshotOnlyAfterCommit() {
        long id = assignment();
        org.mockito.Mockito.clearInvocations(messaging);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            var job = store.claim(id);
            store.complete(job, java.util.List.of(new com.nihongo.staff.service.monitor.collection.NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 25D, null, null)), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            org.mockito.Mockito.verifyNoInteractions(messaging);
        });
        var frame = org.mockito.ArgumentCaptor.forClass(com.nihongo.staff.service.monitor.realtime.PerformanceSocketPublisher.Update.class);
        org.mockito.Mockito.verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.startsWith("/topic/vps-performance/"), frame.capture());
        assertEquals(25D, frame.getValue().performance().objects().get(0).points().get(0).value());
        assertEquals("UP", frame.getValue().performance().state());
    }
    @Test void rolledBackCollectionDoesNotSendSocketData() {
        long id = assignment();
        org.mockito.Mockito.clearInvocations(messaging);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            store.complete(store.claim(id), java.util.List.of(new com.nihongo.staff.service.monitor.collection.NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 40D, null, null)), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(messaging);
    }

    @Autowired MonitorVpsServiceImplI vpsService;

	@Test
	void contextLoads() {
	}

    @Test
    void missingSshPasswordFailsOnlyWhenSyncIsRequested() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> vpsService.sync());
        assertNotNull(error.getCause());
        assertTrue(error.getCause().getMessage().contains("MONITORING_PROMETHEUS_SSH_PASSWORD"));
    }

}
