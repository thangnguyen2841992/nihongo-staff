package com.nihongo.staff.service.monitor.collection;

import org.junit.jupiter.api.Test;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionRunnerTest {
    @Test void savesFailureWithoutInvokingCompletionOnNetworkError() {
        var store = mock(PerfCollectionStore.class);
        var source = mock(NodeMetricSource.class);
        var job = new PerfCollectionStore.Job(1, "token", 2, "127.0.0.1", 9100, "CPU_USAGE", "NODE_EXPORTER", 5000);
        when(store.claim(1)).thenReturn(job);
        when(source.fetch("127.0.0.1", 9100, 5000)).thenThrow(new IllegalStateException("Node Exporter timeout"));
        new MetricCollector(store, source).collectAssignment(1);
        verify(store).fail(job, "Node Exporter timeout"); verify(store, never()).complete(any(), any(), any());
    }
    @Test void aClaimedOrDisabledAssignmentDoesNotMakeAnotherHttpRequest() {
        var store = mock(PerfCollectionStore.class); var source = mock(NodeMetricSource.class);
        new MetricCollector(store, source).collectAssignment(1);
        verifyNoInteractions(source);
    }
    @Test void windowsAssignmentUsesWindowsCpuCounters() {
        var store = mock(PerfCollectionStore.class);
        var source = mock(NodeMetricSource.class);
        var job = new PerfCollectionStore.Job(2, "token", 3, "127.0.0.1", 9182, "CPU_USAGE", "WINDOWS_EXPORTER", 5000);
        when(store.claim(2)).thenReturn(job);
        when(source.fetch("127.0.0.1", 9182, 5000)).thenReturn(NodeMetricSource.parse("""
                windows_exporter_build_info{version="0.31.8"} 1
                windows_cpu_time_total{core="0,0",mode="idle"} 70
                windows_cpu_time_total{core="0,0",mode="user"} 30
                """));
        new MetricCollector(store, source).collectAssignment(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NodeMetricSource.Reading>> readings = ArgumentCaptor.forClass(List.class);
        verify(store).complete(eq(job), readings.capture(), any());
        assertEquals(100D, readings.getValue().get(0).counter());
        verify(store, never()).fail(any(), any());
    }
}
