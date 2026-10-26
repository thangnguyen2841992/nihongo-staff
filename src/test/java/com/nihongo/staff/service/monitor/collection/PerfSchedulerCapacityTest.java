package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.repository.MonitorEventRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                mock(MetricCollector.class), transactionManager, mock(MonitorEventRepository.class));
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
        when(assignments.findDueHere(any(), any(), any(Pageable.class))).thenReturn(List.of(due(1, 1), due(2, 2), due(3, 3), due(4, 4)));
        doAnswer(call -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; })
                .when(collector).collectAssignment(anyLong());
        var scheduler = new PerfCollectionScheduler(assignments, mock(MonitorPerfValueRepository.class),
                collector, mock(PlatformTransactionManager.class), mock(MonitorEventRepository.class));
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.ready();
        try {
            scheduler.tick();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            scheduler.tick();
            scheduler.tick();
            verify(assignments, times(1)).findDueHere(any(), any(), any(Pageable.class));
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }

    PerfCollectionScheduler idleScheduler(MonitorVpsMetricRepository assignments) {
        var scheduler = new PerfCollectionScheduler(assignments, mock(MonitorPerfValueRepository.class),
                mock(MetricCollector.class), mock(PlatformTransactionManager.class), mock(MonitorEventRepository.class));
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "maxIdleMs", 60_000L);
        scheduler.ready();
        return scheduler;
    }

    @Test
    void idleSchedulerSleepsUntilTheNextDueCollectionInsteadOfPollingEveryTick() {
        var assignments = mock(MonitorVpsMetricRepository.class);
        when(assignments.findDueHere(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(assignments.findNextDueHere(any())).thenReturn(PerfCollectionStore.now().plusSeconds(30));
        var scheduler = idleScheduler(assignments);
        try {
            for (int i = 0; i < 5; i++) scheduler.tick();
            verify(assignments, times(1)).findDueHere(any(), any(), any(Pageable.class));
            verify(assignments, times(1)).findNextDueHere(any());
            // A changed interval or a new target must not wait for the old wake-up time.
            scheduler.scheduleChanged(new CollectionScheduleChanged());
            scheduler.tick();
            verify(assignments, times(2)).findDueHere(any(), any(), any(Pageable.class));
        } finally { scheduler.shutdown(); }
    }

    @Test
    void overdueOrMissingNextCollectionFallsBackToPollingWithinTheIdleLimit() {
        var assignments = mock(MonitorVpsMetricRepository.class);
        when(assignments.findDueHere(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(assignments.findNextDueHere(any())).thenReturn(PerfCollectionStore.now().minusSeconds(5));
        var scheduler = idleScheduler(assignments);
        try {
            scheduler.tick();
            scheduler.tick();
            verify(assignments, times(2)).findDueHere(any(), any(), any(Pageable.class));
        } finally { scheduler.shutdown(); }
    }

    @Test
    void cleanupAlsoPurgesExpiredEventsWhenEnabled() {
        var events = mock(MonitorEventRepository.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(events.deleteExpired(any())).thenReturn(10000, 7);
        var values = mock(MonitorPerfValueRepository.class);
        var scheduler = new PerfCollectionScheduler(mock(MonitorVpsMetricRepository.class), values,
                mock(MetricCollector.class), transactionManager, events);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "eventRetentionDays", 180);
        ReflectionTestUtils.setField(scheduler, "cleanupMaxBatches", 20);
        scheduler.ready();
        try {
            scheduler.cleanup();
            verify(events, times(2)).deleteExpired(any());
            verify(values, never()).deleteExpired(any());
        } finally { scheduler.shutdown(); }
    }

    @Test
    void slotsStayOnAFixedPerVpsGridSoMetricsOfOneTargetComeDueTogether() {
        var start = java.time.LocalDateTime.of(2026, 10, 23, 12, 0, 7, 400_000_000);
        var thirty = PerfCollectionStore.nextSlot(42, 30, start);
        var sixty = PerfCollectionStore.nextSlot(42, 60, start);
        assertTrue(thirty.isAfter(start) && !thirty.isAfter(start.plusSeconds(30)));
        // A slow run does not shift the grid: computing from a later moment lands on the same slots.
        var later = PerfCollectionStore.nextSlot(42, 30, start.plusSeconds(3));
        assertTrue(later.equals(thirty) || later.equals(thirty.plusSeconds(30)));
        assertEquals(thirty.plusSeconds(30), PerfCollectionStore.nextSlot(42, 30, thirty));
        // Every 60s slot is also a 30s slot of the same VPS, so both metrics share one scrape.
        assertEquals(0, java.time.Duration.between(thirty, sixty).getSeconds() % 30);
        assertTrue(!sixty.isAfter(start.plusSeconds(60)));
    }
    static MonitorVpsMetricRepository.Due due(long id, long vpsId) {
        return new MonitorVpsMetricRepository.Due() {
            @Override public Long getId() { return id; }
            @Override public Long getVpsId() { return vpsId; }
        };
    }

    @Test
    void oneWorkerPerTargetCollectsAllOfItsDueMetrics() throws Exception {
        var assignments = mock(MonitorVpsMetricRepository.class);
        var collector = mock(MetricCollector.class);
        var done = new CountDownLatch(2);
        when(assignments.findDueHere(any(), any(), any(Pageable.class))).thenReturn(List.of(due(1, 7), due(2, 7), due(3, 8), due(4, 7)));
        doAnswer(call -> { done.countDown(); return null; }).when(collector).collectAssignment(anyLong());
        var scheduler = new PerfCollectionScheduler(assignments, mock(MonitorPerfValueRepository.class),
                collector, mock(PlatformTransactionManager.class), mock(MonitorEventRepository.class));
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.ready();
        try {
            scheduler.tick();
            assertTrue(done.await(3, TimeUnit.SECONDS));
            verify(collector).collectAssignment(1);
            verify(collector).collectAssignment(3);
            verify(collector, never()).collectAssignment(2);
            verify(collector, never()).collectAssignment(4);
        } finally { scheduler.shutdown(); }
    }
}
