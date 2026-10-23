package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.repository.MonitorEventRepository;
import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import com.nihongo.staff.repository.MonitorPerfValueRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

@Component @EnableScheduling @RequiredArgsConstructor @Slf4j
public class PerfCollectionScheduler {
    private final MonitorVpsMetricRepository assignments;
    private final MonitorPerfValueRepository values;
    private final MetricCollector collector;
    private final PlatformTransactionManager transactionManager;
    private final MonitorEventRepository eventHistory;
    @Value("${monitoring.collection.enabled:true}") private boolean enabled;
    @Value("${monitoring.collection.retention-days:30}") private int retentionDays;
    @Value("${monitoring.events.retention-days:180}") private int eventRetentionDays;
    @Value("${monitoring.collection.cleanup-max-batches:20}") private int cleanupMaxBatches;
    // Upper bound for sleeping when nothing is due, so rows added by another instance are still picked up.
    @Value("${monitoring.collection.max-idle-ms:5000}") private long maxIdleMs = 5000;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Semaphore capacity = new Semaphore(4);
    private volatile boolean ready;
    // Wall-clock millis before which the database cannot have due work.
    private volatile long idleUntil;
    // Set by schedule changes; checked before idleUntil so a wake-up racing with a tick is never lost.
    private volatile boolean woken;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(1)
    public void ready() { ready = true; }

    /** Schedules, intervals or assignments changed: stop sleeping and look for due work on the next tick. */
    @TransactionalEventListener(fallbackExecution = true)
    public void scheduleChanged(CollectionScheduleChanged event) { woken = true; }

    @Scheduled(fixedDelayString = "${monitoring.collection.tick-ms:500}")
    public void tick() {
        int available = capacity.availablePermits();
        if (!enabled || !ready || available == 0) return;
        if (!woken && System.currentTimeMillis() < idleUntil) return;
        woken = false;
        LocalDateTime now = PerfCollectionStore.now();
        // A worker collects every due metric of one target, so dispatch at most one worker per target.
        var due = assignments.findDue(now, PageRequest.of(0, Math.min(64, available * 8)));
        var targets = new java.util.HashSet<Long>();
        for (var row : due) {
            if (!targets.add(row.getVpsId())) continue;
            long id = row.getId();
            if (!capacity.tryAcquire()) break;
            try {
                workers.execute(() -> {
                    try { collector.collectAssignment(id); } finally { capacity.release(); }
                });
            } catch (RejectedExecutionException e) { capacity.release(); }
        }
        // Nothing due: sleep until the earliest scheduled collection (or lease expiry) instead of polling every tick.
        if (due.isEmpty()) idleUntil = System.currentTimeMillis() + idleMillis(now);
    }

    private long idleMillis(LocalDateTime now) {
        long limit = Math.max(0, maxIdleMs);
        LocalDateTime next = assignments.findNextDue();
        if (next == null) return limit;
        return Math.max(0, Math.min(limit, Duration.between(now, next).toMillis()));
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void cleanup() {
        if (!enabled || !ready) return;
        if (retentionDays > 0) purge("Perf", values::deleteExpired, retentionDays);
        if (eventRetentionDays > 0) purge("Event", eventHistory::deleteExpired, eventRetentionDays);
    }

    private void purge(String name, Function<LocalDateTime, Integer> delete, int days) {
        var cutoff = PerfCollectionStore.now().minusDays(days);
        var transaction = new TransactionTemplate(transactionManager);
        int batches = Math.max(1, cleanupMaxBatches);
        for (int i = 0; i < batches; i++) {
            Integer deleted = transaction.execute(status -> delete.apply(cutoff));
            if (deleted == null || deleted < 10000) return;
        }
        log.info("{} retention cleanup reached {} batches; remaining data will be processed next hour", name, batches);
    }
    @PreDestroy public void shutdown() { workers.shutdownNow(); }
}
