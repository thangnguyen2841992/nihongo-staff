package com.nihongo.staff.service.monitor.vps;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.nihongo.staff.model.monitoring.dto.RegisterMonitorVpsRequest;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import com.nihongo.staff.service.monitor.collection.PerfCollectionStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MonitorVpsTransactionTest {
    @Test
    void probesOutsideTheTransactionAndPersistsAllRegistrationStepsTogether() {
        var servers = mock(MonitorVpsRepository.class);
        var source = mock(NodeMetricSource.class);
        var catalog = mock(MetricCatalog.class);
        var store = mock(PerfCollectionStore.class);
        var targets = mock(PrometheusTargetService.class);
        var manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
        when(source.fetch("host", 9100, 5000)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(new NodeMetricSource.Sample("node_exporter_build_info", java.util.Map.of(), 1));
        });
        when(servers.save(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.getArgument(0);
        });
        doAnswer(invocation -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return null; })
                .when(catalog).bindDefaults(any());
        doAnswer(invocation -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return null; })
                .when(store).discover(any(), any());
        var service = new MonitorVpsService(servers, source, catalog, store, targets, manager);
        var request = new RegisterMonitorVpsRequest();
        request.setIpAddress("host");
        request.setHostname("server");
        assertEquals("server", service.registerVps(request).getHostname());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        verify(catalog).bindDefaults(any());
        verify(store).discover(any(), any());
    }
}
