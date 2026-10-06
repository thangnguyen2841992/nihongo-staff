package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import com.nihongo.staff.repository.MonitorPerfValueRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.*;

@Component @EnableScheduling @RequiredArgsConstructor @Slf4j
public class PerfCollectionScheduler {
    private final MonitorVpsMetricRepository assignments;
    private final MonitorPerfValueRepository values;
    private final MetricCollector collector;
    private final PlatformTransactionManager transactionManager;
    @Value("${monitoring.collection.enabled:true}") private boolean enabled;
    @Value("${monitoring.collection.retention-days:0}") private int retentionDays;
    @Value("${monitoring.collection.cleanup-max-batches:20}") private int cleanupMaxBatches;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Semaphore capacity = new Semaphore(4);
    private volatile boolean ready;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(1)
    public void ready() { ready = true; }

    @Scheduled(fixedDelayString = "${monitoring.collection.tick-ms:1000}")
    public void tick() {
        int available = capacity.availablePermits();
        if (!enabled || !ready || available == 0) return;
        for (Long id : assignments.findDue(PerfCollectionStore.now(), PageRequest.of(0, available))) {
            if (!capacity.tryAcquire()) break;
            try {
                workers.execute(() -> {
                    try { collector.collectAssignment(id); } finally { capacity.release(); }
                });
            } catch (RejectedExecutionException e) { capacity.release(); }
        }
    }
    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void cleanup() {
        if (!enabled || !ready || retentionDays < 1) return;
        var cutoff = PerfCollectionStore.now().minusDays(retentionDays);
        var transaction = new TransactionTemplate(transactionManager);
        int batches = Math.max(1, cleanupMaxBatches);
        for (int i = 0; i < batches; i++) {
            Integer deleted = transaction.execute(status -> values.deleteExpired(cutoff));
            if (deleted == null || deleted < 10000) return;
        }
        log.info("Perf retention cleanup reached {} batches; remaining data will be processed next hour", batches);
    }
    @PreDestroy public void shutdown() { workers.shutdownNow(); }
}
