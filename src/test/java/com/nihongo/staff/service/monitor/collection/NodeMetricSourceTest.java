package com.nihongo.staff.service.monitor.collection;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NodeMetricSourceTest {
    @Test void parsesCrlfEscapedLabelsAndIgnoresNonFiniteValues() {
        var samples = NodeMetricSource.parse("# HELP ignored\r\nnode_network_receive_bytes_total{device=\"eth\\\"0\"} 10\r\nnode_load1 NaN\r\nnode_unrelated +Inf\n");
        assertEquals(1, samples.size()); assertEquals("eth\"0", samples.get(0).labels().get("device"));
    }
    @Test void cpuObjectsExcludeGuestCountersAlreadyIncludedInUser() {
        var readings = NodeMetricSource.readings("CPU_USAGE", NodeMetricSource.parse("node_cpu_seconds_total{cpu=\"0\",mode=\"idle\"} 70\nnode_cpu_seconds_total{cpu=\"0\",mode=\"user\"} 30\nnode_cpu_seconds_total{cpu=\"0\",mode=\"guest\"} 10\nnode_cpu_seconds_total{cpu=\"1\",mode=\"idle\"} 90\n"));
        assertEquals(2, readings.size()); assertEquals(100D, readings.get(0).counter()); assertNull(readings.get(0).value());
    }
    @Test void filesystemIdentityIncludesBothDeviceAndMountpoint() {
        var samples = NodeMetricSource.parse("node_filesystem_size_bytes{device=\"/dev/vda1\",mountpoint=\"/\",fstype=\"ext4\"} 100\nnode_filesystem_avail_bytes{device=\"/dev/vda1\",mountpoint=\"/\",fstype=\"ext4\"} 40\nnode_filesystem_size_bytes{device=\"/dev/vda1\",mountpoint=\"/data\",fstype=\"ext4\"} 100\nnode_filesystem_avail_bytes{device=\"/dev/vda1\",mountpoint=\"/data\",fstype=\"ext4\"} 80\n");
        var readings = NodeMetricSource.readings("DISK_USAGE", samples);
        assertEquals(2, readings.size()); assertNotEquals(readings.get(0).key(), readings.get(1).key()); assertEquals(60D, readings.get(0).value());
    }
    @Test void networkCountersRemainSeparatePerDevice() {
        var readings = NodeMetricSource.readings("NETWORK_RECEIVE", NodeMetricSource.parse("node_network_receive_bytes_total{device=\"lo\"} 999\nnode_network_receive_bytes_total{device=\"eth0\"} 120\nnode_network_receive_bytes_total{device=\"eth1\"} 240\n"));
        assertEquals(2, readings.size()); assertEquals("eth0", readings.get(0).key()); assertEquals(240D, readings.get(1).counter());
    }
    @Test void missingMemoryMetricDoesNotBecomeZero() { assertTrue(NodeMetricSource.readings("MEMORY_USAGE", List.of()).isEmpty()); }
    @Test void objectKeysAreBoundedAndDelimiterSafe() {
        assertNotEquals(NodeMetricSource.key(Map.of("device", "ab", "mountpoint", "c")), NodeMetricSource.key(Map.of("device", "a", "mountpoint", "bc")));
        assertTrue(NodeMetricSource.key(Map.of("device", "a".repeat(500))).length() <= 255);
    }
    @Test void explainsConnectionAndResponseTimeouts() {
        assertTrue(NodeMetricSource.failureReason(new java.util.concurrent.ExecutionException(new java.net.ConnectException("timed out"))).contains("kết nối"));
        assertTrue(NodeMetricSource.failureReason(new java.util.concurrent.TimeoutException()).contains("thời gian"));
        assertTrue(NodeMetricSource.failureReason(new IllegalStateException("Node Exporter trả HTTP 404")).contains("404"));
    }
}
