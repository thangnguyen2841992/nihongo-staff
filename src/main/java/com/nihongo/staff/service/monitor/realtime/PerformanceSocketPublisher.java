package com.nihongo.staff.service.monitor.realtime;

import com.nihongo.staff.service.monitor.collection.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.atomic.AtomicLong;

@Component @RequiredArgsConstructor @Slf4j
public class PerformanceSocketPublisher {
    public record Update(long sequence, VpsPerformanceService.Performance performance) {}
    private final VpsPerformanceService performance;
    private final SimpMessagingTemplate messaging;
    private final AtomicLong sequence = new AtomicLong();

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public synchronized void changed(PerformanceChanged event) {
        try {
            var snapshot = performance.read(event.vpsId(), event.metricCode(), null, null);
            messaging.convertAndSend(destination(event.vpsId(), event.metricCode()), new Update(sequence.incrementAndGet(), snapshot));
        } catch (Exception e) {
            // A disconnected broker must never turn a committed collection into a failure.
            log.warn("Cannot publish VPS performance: vps={}, metric={}, reason={}", event.vpsId(), event.metricCode(), e.getClass().getSimpleName());
        }
    }
    public static String destination(long vpsId, String code) { return "/topic/vps-performance/" + vpsId + "/" + code; }
}
