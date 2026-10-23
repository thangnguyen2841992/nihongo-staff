package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.model.monitoring.dto.CheckTargetRequest;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.check.*;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:check_targets;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PerfPersistenceTest.Config.class)
@Import(CheckTargetService.class)
class CheckPersistenceTest {
    @Autowired CheckTargetService service;
    @Autowired MetricCatalog catalog;
    @Autowired MonitorVpsMetricRepository links;
    @Autowired MonitorVpsRepository servers;
    @Autowired MonitorPerfValueRepository values;
    @Autowired MonitorEventRepository events;
    @Autowired MonitorEventService rules;
    @Autowired PerfCollectionStore store;
    @Autowired VpsPerformanceService performance;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired MonitorCheckTargetRepository targets;
    @MockitoBean CheckMetricSource source;

    @Test void loadsOnlyOnePageWithOneScalarQueryAndNoEntityHydration() {
        catalog.initialize();
        for (int index = 0; index < 21; index++)
            service.register(new CheckTargetRequest("port-" + index, "PORT_CHECK", "localhost", 10000 + index, null, null, null));
        for (int index = 0; index < 3; index++)
            service.register(new CheckTargetRequest("web-" + index, "WEB_CHECK", null, null, "http://localhost/" + index, 200, 399));
        entityManager.flush(); entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true); statistics.clear();
        try {
            var first = service.page("PORT_CHECK", 0, 20);
            assertEquals(20, first.content().size()); assertTrue(first.hasNext());
            assertEquals("port-20", first.content().get(0).name());
            assertEquals(1, statistics.getPrepareStatementCount()); assertEquals(0, statistics.getEntityLoadCount());
            var second = service.page("PORT_CHECK", 1, 20);
            assertEquals(1, second.content().size()); assertFalse(second.hasNext());
            assertEquals("port-0", second.content().get(0).name());
            assertEquals(3, service.page("WEB_CHECK", 0, 20).content().size());
            statistics.clear();
            var connection = targets.connection(first.content().get(0).vpsId()).orElseThrow();
            assertEquals(10020, connection.getPort()); assertEquals("port-20", connection.getName());
            assertEquals(1, statistics.getPrepareStatementCount()); assertEquals(0, statistics.getEntityLoadCount());
        } finally { statistics.setStatisticsEnabled(enabled); }
        for (String type : new String[]{"NODE_EXPORTER", "", "wrong"})
            assertThrows(ResponseStatusException.class, () -> service.page(type, 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.page("PORT_CHECK", -1, 20));
        assertThrows(ResponseStatusException.class, () -> service.page("PORT_CHECK", 0, 101));
    }

    @Test void registersPortAndDifferentWebPathsOnTheSameEndpointAndRejectsDuplicates() {
        catalog.initialize();
        var port = service.register(new CheckTargetRequest("tcp", "PORT_CHECK", "localhost", 80, null, null, null));
        var first = service.register(new CheckTargetRequest("web", "WEB_CHECK", null, null, "http://localhost/a", 200, 299));
        service.register(new CheckTargetRequest("web-b", "WEB_CHECK", null, null, "http://localhost/b", 200, 399));
        assertEquals("UNKNOWN", first.status()); assertEquals(3, targets.count());
        assertEquals(2, links.findByVps_VpsIdOrderByMetric_MetricNameAsc(port.vpsId()).size());
        assertEquals(3, links.findByVps_VpsIdOrderByMetric_MetricNameAsc(first.vpsId()).size());
        assertThrows(ResponseStatusException.class, () -> service.register(new CheckTargetRequest("duplicate", "WEB_CHECK", null, null, "http://LOCALHOST:80/a", null, null)));
        verifyNoInteractions(source);
    }
    @Test void savesOutagesAsZeroAndEmitsAlertThenRecoveryWithCorrectTargetStatus() {
        catalog.initialize();
        long id = service.register(new CheckTargetRequest("web", "WEB_CHECK", null, null, "http://localhost/health", 200, 399)).vpsId();
        rules.save(id, "WEB_AVAILABLE", null, new MonitorEventService.Input("Web down", "vps", MonitorEventRule.Operator.LT,
                1D, MonitorEventRule.Severity.CRITICAL, 1, true));
        var link = links.findByVps_VpsIdAndMetric_MetricCode(id, "WEB_AVAILABLE").orElseThrow();
        var at = PerfCollectionStore.now().minusSeconds(30).withNano(0);
        var down = new CheckMetricSource.Probe(false, 5000, null, "Hết thời gian kiểm tra.");
        store.completeCheck(store.claim(link.getVpsMetricId()), CheckMetricSource.readings("WEB_AVAILABLE", down), at, down.message());
        assertEquals(0D, values.findAll().get(0).getValue()); assertEquals(VpsStatus.DOWN, servers.findById(id).orElseThrow().getStatus());
        assertEquals("DOWN", performance.read(id, "WEB_AVAILABLE", null, null).state());
        assertFalse(performance.read(id, "WEB_AVAILABLE", null, null).objects().get(0).stale(), "A newly measured outage is valid data");
        assertEquals(MonitorEvent.Kind.ALERT, events.findAll().get(0).getKind());
        link.setNextCollectionAt(at);
        var up = new CheckMetricSource.Probe(true, 10, 200, "OK");
        store.completeCheck(store.claim(link.getVpsMetricId()), CheckMetricSource.readings("WEB_AVAILABLE", up), at.plusSeconds(30), null);
        assertEquals("UP", performance.read(id, "WEB_AVAILABLE", null, null).state());
        assertEquals(VpsStatus.UP, servers.findById(id).orElseThrow().getStatus());
        assertTrue(events.findAll().stream().anyMatch(event -> event.getKind() == MonitorEvent.Kind.RECOVERY));
        link.setNextCollectionAt(at);
        store.fail(store.claim(link.getVpsMetricId()), "Thiếu cấu hình kiểm tra");
        assertTrue(performance.read(id, "WEB_AVAILABLE", null, null).objects().get(0).stale(), "An internal collector failure leaves stale data");
    }
}
