package com.nihongo.staff.service.monitor.realtime;

import com.nihongo.staff.service.monitor.collection.PerformanceChanged;
import com.nihongo.staff.service.monitor.collection.VpsPerformanceService;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import com.nihongo.staff.service.monitor.event.MonitorEventsChanged;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SocketPublisherCoalescingTest {
    final VpsPerformanceService performance = mock(VpsPerformanceService.class);
    final MonitorEventService events = mock(MonitorEventService.class);
    final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    final RealtimeSubscriptions subscriptions = mock(RealtimeSubscriptions.class);

    @Test void collectionsNobodyWatchesNeverReadTheDatabase() {
        var publisher = new PerformanceSocketPublisher(performance, messaging, subscriptions);
        for (int i = 0; i < 10; i++) publisher.changed(new PerformanceChanged(1, "CPU_USAGE"));
        publisher.flush();
        verifyNoInteractions(performance, messaging);
    }

    @Test void burstOfChangesForOneTopicIsReadAndSentOnce() {
        when(subscriptions.hasSubscribers(PerformanceSocketPublisher.destination(1, "CPU_USAGE"))).thenReturn(true);
        var snapshot = new VpsPerformanceService.Performance(1, "CPU_USAGE", "UP", null, List.of(), 30, List.of());
        when(performance.read(1, "CPU_USAGE", null, null)).thenReturn(snapshot);
        var publisher = new PerformanceSocketPublisher(performance, messaging, subscriptions);
        for (int i = 0; i < 5; i++) publisher.changed(new PerformanceChanged(1, "CPU_USAGE"));
        publisher.changed(new PerformanceChanged(1, "MEMORY_USAGE"));
        publisher.flush();
        publisher.flush();
        verify(performance, times(1)).read(anyLong(), anyString(), any(), any());
        verify(messaging, times(1)).convertAndSend(eq(PerformanceSocketPublisher.destination(1, "CPU_USAGE")), any(Object.class));
    }

    @Test void viewerLeavingBeforeFlushSkipsTheRead() {
        when(subscriptions.hasSubscribers(anyString())).thenReturn(true, false);
        var publisher = new PerformanceSocketPublisher(performance, messaging, subscriptions);
        publisher.changed(new PerformanceChanged(1, "CPU_USAGE"));
        publisher.flush();
        verifyNoInteractions(performance, messaging);
    }

    @Test void eventIdsAreMergedPerVpsAndUnwatchedVpsAreDropped() {
        when(subscriptions.hasSubscribers("/topic/vps-events/7")).thenReturn(true);
        var event = mock(MonitorEventService.Event.class);
        when(events.committed(eq(7L), anyList())).thenReturn(List.of(event));
        var publisher = new MonitorEventSocketPublisher(events, messaging, subscriptions);
        publisher.changed(new MonitorEventsChanged(7, List.of(1L, 2L)));
        publisher.changed(new MonitorEventsChanged(7, List.of(3L)));
        publisher.changed(new MonitorEventsChanged(8, List.of(4L)));
        publisher.flush();
        @SuppressWarnings("unchecked")
        var ids = (org.mockito.ArgumentCaptor<List<Long>>) (Object) org.mockito.ArgumentCaptor.forClass(List.class);
        verify(events, times(1)).committed(eq(7L), ids.capture());
        assertEquals(Set.of(1L, 2L, 3L), Set.copyOf(ids.getValue()));
        verify(events, never()).committed(eq(8L), anyList());
        verify(messaging, times(1)).convertAndSend(eq("/topic/vps-events/7"), any(Object.class));
    }

    @Test void registryLookupMatchesExactDestination() {
        var registry = mock(SimpUserRegistry.class);
        var user = mock(SimpUser.class);
        var session = mock(SimpSession.class);
        var subscription = mock(SimpSubscription.class);
        when(registry.getUsers()).thenReturn(Set.of(user));
        when(user.getSessions()).thenReturn(Set.of(session));
        when(session.getSubscriptions()).thenReturn(Set.of(subscription));
        when(subscription.getDestination()).thenReturn("/topic/vps-performance/1/CPU_USAGE");
        var lookup = new RealtimeSubscriptions(registry);
        assertTrue(lookup.hasSubscribers("/topic/vps-performance/1/CPU_USAGE"));
        assertFalse(lookup.hasSubscribers("/topic/vps-performance/12/CPU_USAGE"));
    }
}
