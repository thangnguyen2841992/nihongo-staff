package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.repository.MonitorPerfValueRepository;
import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerfSchedulerCapacityTest {
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
