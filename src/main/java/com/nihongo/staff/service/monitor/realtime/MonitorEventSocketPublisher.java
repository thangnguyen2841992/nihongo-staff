package com.nihongo.staff.service.monitor.realtime;

import com.nihongo.staff.service.monitor.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Same pattern as {@link PerformanceSocketPublisher}: record committed event ids, read and send them off the worker thread. */
@Component @RequiredArgsConstructor
public class MonitorEventSocketPublisher {
    private static final org.slf4j.Logger trace = org.slf4j.LoggerFactory.getLogger("nihongo.socket.monitor-events");
    public record Update(long sequence, long vpsId, List<MonitorEventService.Event> events) {}
    private final MonitorEventService events;
    private final SimpMessagingTemplate messaging;
    private final RealtimeSubscriptions subscriptions;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Set<Long>> pending = new ConcurrentHashMap<>();

    @TransactionalEventListener
    public void changed(MonitorEventsChanged event) {
        // Clients resync recent events from the database when they (re)subscribe, so unwatched ids can be dropped.
        if (!subscriptions.hasSubscribers(destination(event.vpsId()))) return;
        pending.merge(event.vpsId(), Set.copyOf(event.eventIds()), (current, added) -> {
            var union = new HashSet<>(current); union.addAll(added); return Set.copyOf(union);
        });
    }

    @Scheduled(fixedDelayString = "${monitoring.realtime.flush-ms:1000}")
    public synchronized void flush() {
        if (pending.isEmpty()) return;
        for (Long vpsId : List.copyOf(pending.keySet())) {
            Set<Long> ids = pending.remove(vpsId);
            if (ids == null || ids.isEmpty() || !subscriptions.hasSubscribers(destination(vpsId))) continue;
            try {
                var committed = events.committed(vpsId, List.copyOf(ids));
                if (!committed.isEmpty()) {
                    messaging.convertAndSend(destination(vpsId), new Update(sequence.incrementAndGet(), vpsId, committed));
                    trace.debug("event=published vpsId={} sequence={} count={}", vpsId, sequence.get(), committed.size());
                }
            } catch (Exception e) {
                trace.warn("event=publish-failed vpsId={} failure={}", vpsId, e.getClass().getSimpleName());
            }
        }
    }
    static String destination(long vpsId) { return "/topic/vps-events/" + vpsId; }
}
