package com.nihongo.staff.service.monitor.realtime;

import com.nihongo.staff.service.monitor.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collection workers only mark a (VPS, metric) as changed after commit. {@link #flush()} later reads one snapshot per
 * changed destination that someone is subscribed to, so a burst of collections costs one read and nothing is read
 * while no staff member has the page open. Workers never wait on this class.
 */
@Component @RequiredArgsConstructor
public class PerformanceSocketPublisher {
    private static final org.slf4j.Logger events = org.slf4j.LoggerFactory.getLogger("nihongo.socket.performance");
    public record Update(long sequence, VpsPerformanceService.Performance performance) {}
    private record Key(long vpsId, String metricCode) {}
    private final VpsPerformanceService performance;
    private final SimpMessagingTemplate messaging;
    private final RealtimeSubscriptions subscriptions;
    private final AtomicLong sequence = new AtomicLong();
    private final Set<Key> pending = ConcurrentHashMap.newKeySet();

    @TransactionalEventListener
    public void changed(PerformanceChanged event) {
        if (subscriptions.hasSubscribers(destination(event.vpsId(), event.metricCode())))
            pending.add(new Key(event.vpsId(), event.metricCode()));
    }

    // synchronized only orders flushes against each other (scheduler vs. explicit calls); collectors never enter it.
    @Scheduled(fixedDelayString = "${monitoring.realtime.flush-ms:1000}")
    public synchronized void flush() {
        if (pending.isEmpty()) return;
        for (Key key : List.copyOf(pending)) {
            pending.remove(key);
            String destination = destination(key.vpsId(), key.metricCode());
            if (!subscriptions.hasSubscribers(destination)) continue;
            try {
                var snapshot = performance.read(key.vpsId(), key.metricCode(), null, null);
                messaging.convertAndSend(destination, new Update(sequence.incrementAndGet(), snapshot));
                events.debug("event=published vpsId={} metric={} sequence={}", key.vpsId(), key.metricCode(), sequence.get());
            } catch (Exception e) {
                // A disconnected broker or a deleted assignment must never stop other frames.
                events.warn("event=publish-failed vpsId={} metric={} failure={}", key.vpsId(), key.metricCode(), e.getClass().getSimpleName());
            }
        }
    }
    public static String destination(long vpsId, String code) { return "/topic/vps-performance/" + vpsId + "/" + code; }
}
