package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.service.monitor.mysql.MysqlMetricSource;
import com.nihongo.staff.service.monitor.kafka.KafkaMetricSource;
import com.nihongo.staff.service.monitor.check.CheckMetricSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionRunnerTest {
    @Test void checkerOverloadDoesNotCreateAFalseOutageSample() {
        var store = mock(PerfCollectionStore.class); var checks = mock(CheckMetricSource.class);
        var job = new PerfCollectionStore.Job(7, "token", 8, "example.com", 443, "WEB_AVAILABLE", "WEB_CHECK", 5000);
        when(store.claimGroup(7)).thenReturn(List.of(job));
        when(checks.collect(8, 5000)).thenThrow(new CheckMetricSource.ProbeUnavailableException());
        new MetricCollector(store, mock(NodeMetricSource.class), mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), checks).collectAssignment(7);
        verify(store).fail(eq(job), contains("DNS đang bận"));
        verify(store, never()).completeCheck(any(), any(), any(), any());
    }
    @Test void failedWebCheckStillRecordsAvailabilityAndPassesReasonToStore() {
        var store = mock(PerfCollectionStore.class); var checks = mock(CheckMetricSource.class);
        var job = new PerfCollectionStore.Job(7, "token", 8, "example.com", 443, "WEB_AVAILABLE", "WEB_CHECK", 5000);
        when(store.claimGroup(7)).thenReturn(List.of(job));
        when(checks.collect(8, 5000)).thenReturn(new CheckMetricSource.Probe(false, 10D, 503, "HTTP 503"));
        new MetricCollector(store, mock(NodeMetricSource.class), mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), checks).collectAssignment(7);
        verify(store).completeCheck(eq(job), argThat(readings -> readings.get(0).value() == 0D), any(), eq("HTTP 503"));
        verify(store, never()).fail(any(), any());
    }
    @Test void metricsForOneExporterShareAScrapeWithoutMixingDifferentEndpoints() {
        var store = mock(PerfCollectionStore.class);
        var source = mock(NodeMetricSource.class);
        var first = new PerfCollectionStore.Job(1, "a", 2, "host-a", 9100, "LOAD_1M", "NODE_EXPORTER", 5000);
        var second = new PerfCollectionStore.Job(2, "b", 2, "host-a", 9100, "UPTIME", "NODE_EXPORTER", 5000);
        var moved = new PerfCollectionStore.Job(3, "c", 2, "host-b", 9100, "LOAD_1M", "NODE_EXPORTER", 5000);
        when(store.claimGroup(1)).thenReturn(List.of(first));
        when(store.claimGroup(2)).thenReturn(List.of(second));
        when(store.claimGroup(3)).thenReturn(List.of(moved));
        when(source.fetch(anyString(), eq(9100), eq(5000))).thenReturn(NodeMetricSource.parse("""
                node_exporter_build_info{version="1"} 1
                node_load1 2
                node_time_seconds 100
                node_boot_time_seconds 10
                """));
        var collector = new MetricCollector(store, source, mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), mock(CheckMetricSource.class));
        collector.collectAssignment(1);
        collector.collectAssignment(2);
        collector.collectAssignment(3);
        verify(source, times(1)).fetch("host-a", 9100, 5000);
        verify(source, times(1)).fetch("host-b", 9100, 5000);
        verify(store).complete(eq(first), argThat(rows -> rows.size() == 1 && rows.get(0).value() == 2D), any());
        verify(store).complete(eq(second), argThat(rows -> rows.size() == 1 && rows.get(0).value() == 90D), any());
        verify(store, never()).fail(any(), any());
    }
    @Test void savesFailureWithoutInvokingCompletionOnNetworkError() {
        var store = mock(PerfCollectionStore.class);
        var source = mock(NodeMetricSource.class);
        var job = new PerfCollectionStore.Job(1, "token", 2, "127.0.0.1", 9100, "CPU_USAGE", "NODE_EXPORTER", 5000);
        when(store.claimGroup(1)).thenReturn(List.of(job));
        when(source.fetch("127.0.0.1", 9100, 5000)).thenThrow(new IllegalStateException("Node Exporter timeout"));
        new MetricCollector(store, source, mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), mock(CheckMetricSource.class)).collectAssignment(1);
        verify(store).fail(job, "Node Exporter timeout"); verify(store, never()).complete(any(), any(), any());
    }
    @Test void aClaimedOrDisabledAssignmentDoesNotMakeAnotherHttpRequest() {
        var store = mock(PerfCollectionStore.class); var source = mock(NodeMetricSource.class);
        new MetricCollector(store, source, mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), mock(CheckMetricSource.class)).collectAssignment(1);
        verifyNoInteractions(source);
    }
    @Test void windowsAssignmentUsesWindowsCpuCounters() {
        var store = mock(PerfCollectionStore.class);
        var source = mock(NodeMetricSource.class);
        var job = new PerfCollectionStore.Job(2, "token", 3, "127.0.0.1", 9182, "CPU_USAGE", "WINDOWS_EXPORTER", 5000);
        when(store.claimGroup(2)).thenReturn(List.of(job));
        when(source.fetch("127.0.0.1", 9182, 5000)).thenReturn(NodeMetricSource.parse("""
                windows_exporter_build_info{version="0.31.8"} 1
                windows_cpu_time_total{core="0,0",mode="idle"} 70
                windows_cpu_time_total{core="0,0",mode="user"} 30
                """));
        new MetricCollector(store, source, mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), mock(CheckMetricSource.class)).collectAssignment(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NodeMetricSource.Reading>> readings = ArgumentCaptor.forClass(List.class);
        verify(store).complete(eq(job), readings.capture(), any());
        assertEquals(100D, readings.getValue().get(0).counter());
        verify(store, never()).fail(any(), any());
    }
    @Test void mysqlAssignmentUsesMysqlSourceOnly() {
        var store = mock(PerfCollectionStore.class);
        var node = mock(NodeMetricSource.class);
        var mysql = mock(MysqlMetricSource.class);
        var job = new PerfCollectionStore.Job(3, "token", 4, "127.0.0.1", 3306, "MYSQL_THREADS_CONNECTED", "MYSQL_JDBC", 5000);
        when(store.claimGroup(3)).thenReturn(List.of(job));
        var reading = new NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 3D, null, null);
        when(mysql.readings(4, "127.0.0.1", 3306, "MYSQL_THREADS_CONNECTED", 5000)).thenReturn(List.of(reading));
        new MetricCollector(store, node, mysql, mock(KafkaMetricSource.class), mock(CheckMetricSource.class)).collectAssignment(3);
        verify(store).complete(eq(job), eq(List.of(reading)), any());
        verifyNoInteractions(node);
    }
    @Test void kafkaAssignmentUsesKafkaSourceOnly() {
        var store = mock(PerfCollectionStore.class);
        var node = mock(NodeMetricSource.class);
        var mysql = mock(MysqlMetricSource.class);
        var kafka = mock(KafkaMetricSource.class);
        var job = new PerfCollectionStore.Job(4, "token", 5, "broker-a", 9092,
                "KAFKA_BROKER_COUNT", "KAFKA_ADMIN", 10000);
        when(store.claimGroup(4)).thenReturn(List.of(job));
        var reading = new NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 3D, null, null);
        when(kafka.readings(5, "KAFKA_BROKER_COUNT", 10000)).thenReturn(List.of(reading));

        new MetricCollector(store, node, mysql, kafka, mock(CheckMetricSource.class)).collectAssignment(4);

        verify(store).complete(eq(job), eq(List.of(reading)), any());
        verifyNoInteractions(node, mysql);
    }
    @Test void metricsOfOneTargetAreReadOnceAndSavedInOneTransaction() {
        var store = mock(PerfCollectionStore.class); var mysql = mock(MysqlMetricSource.class);
        var connected = new PerfCollectionStore.Job(3, "a", 4, "db", 3306, "MYSQL_THREADS_CONNECTED", "MYSQL_JDBC", 5000);
        var queries = new PerfCollectionStore.Job(5, "b", 4, "db", 3306, "MYSQL_QUERIES_RATE", "MYSQL_JDBC", 5000);
        when(store.claimGroup(3)).thenReturn(List.of(connected, queries));
        var reading = new NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 3D, null, null);
        when(mysql.readings(eq(4L), eq("db"), eq(3306), anyString(), eq(5000))).thenReturn(List.of(reading));
        when(mysql.readings(4, "db", 3306, "MYSQL_QUERIES_RATE", 5000)).thenThrow(new IllegalStateException("MySQL timeout"));
        when(store.finish(any())).thenReturn(java.util.Set.of(5L));
        new MetricCollector(store, mock(NodeMetricSource.class), mysql, mock(KafkaMetricSource.class), mock(CheckMetricSource.class)).collectAssignment(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfCollectionStore.Outcome>> saved = ArgumentCaptor.forClass(List.class);
        verify(store).finish(saved.capture());
        assertEquals(List.of(reading), saved.getValue().get(0).readings());
        assertEquals("MySQL timeout", saved.getValue().get(1).failure());
        verify(store, never()).complete(any(), any(), any());
        verify(store, never()).fail(any(), any());
    }
    @Test void failedGroupSaveFallsBackToSavingEachMetric() {
        var store = mock(PerfCollectionStore.class); var checks = mock(CheckMetricSource.class);
        var available = new PerfCollectionStore.Job(7, "a", 8, "example.com", 443, "WEB_AVAILABLE", "WEB_CHECK", 5000);
        var status = new PerfCollectionStore.Job(9, "b", 8, "example.com", 443, "WEB_STATUS_CODE", "WEB_CHECK", 5000);
        when(store.claimGroup(7)).thenReturn(List.of(available, status));
        when(checks.collect(8, 5000)).thenReturn(new CheckMetricSource.Probe(true, 12D, 200, "OK"));
        when(store.finish(any())).thenThrow(new IllegalStateException("deadlock"));
        new MetricCollector(store, mock(NodeMetricSource.class), mock(MysqlMetricSource.class), mock(KafkaMetricSource.class), checks).collectAssignment(7);
        verify(store).completeCheck(eq(available), argThat(rows -> rows.get(0).value() == 1D), any(), isNull());
        verify(store).completeCheck(eq(status), argThat(rows -> rows.get(0).value() == 200D), any(), isNull());
    }
}
