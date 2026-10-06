package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.repository.MonitorPerfValueRepository;
import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerfSchedulerCapacityTest {
    @Test
    void cleanupContinuesInBoundedTransactionsUntilExpiredRowsAreDrained() {
        var values = mock(MonitorPerfValueRepository.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(values.deleteExpired(any())).thenReturn(10000, 10000, 5);
        var scheduler = new PerfCollectionScheduler(mock(MonitorVpsMetricRepository.class), values,
                mock(MetricCollector.class), transactionManager);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
        ReflectionTestUtils.setField(scheduler, "cleanupMaxBatches", 20);
        scheduler.ready();

        scheduler.cleanup();

        verify(values, times(3)).deleteExpired(any());
        verify(transactionManager, times(3)).commit(any());
        scheduler.shutdown();
    }

    @Test
    void busyWorkersDoNotKeepPollingTheDatabase() throws Exception {
        var assignments = mock(MonitorVpsMetricRepository.class);
        var collector = mock(MetricCollector.class);
        var started = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        when(assignments.findDue(any(), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L, 4L));
        doAnswer(call -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; })
                .when(collector).collectAssignment(anyLong());
        var scheduler = new PerfCollectionScheduler(assignments, mock(MonitorPerfValueRepository.class),
                collector, mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.ready();
        try {
            scheduler.tick();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            scheduler.tick();
            scheduler.tick();
            verify(assignments, times(1)).findDue(any(), any(Pageable.class));
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }
}
