package com.nihongo.staff.service.monitor.realtime;

import com.nihongo.staff.service.monitor.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component @RequiredArgsConstructor @Slf4j
public class MonitorEventSocketPublisher {
    private static final org.slf4j.Logger trace = org.slf4j.LoggerFactory.getLogger("nihongo.socket.monitor-events");
    public record Update(long sequence, long vpsId, List<MonitorEventService.Event> events) {}
    private final MonitorEventService events;
    private final SimpMessagingTemplate messaging;
    private final AtomicLong sequence = new AtomicLong();
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public synchronized void changed(MonitorEventsChanged event) {
        try {
            var committed = events.committed(event.vpsId(), event.eventIds());
            if (!committed.isEmpty()) {
                messaging.convertAndSend("/topic/vps-events/" + event.vpsId(), new Update(sequence.incrementAndGet(), event.vpsId(), committed));
                trace.debug("event=published vpsId={} sequence={} count={}", event.vpsId(), sequence.get(), committed.size());
            }
        } catch (Exception e) {
            trace.warn("event=publish-failed vpsId={} failure={}", event.vpsId(), e.getClass().getSimpleName());
        }
    }
}
