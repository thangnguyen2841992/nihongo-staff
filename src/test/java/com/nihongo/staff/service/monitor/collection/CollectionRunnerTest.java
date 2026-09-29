package com.nihongo.staff.service.monitor.collection;

import org.junit.jupiter.api.Test;
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
}
