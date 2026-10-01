package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.ExporterType;
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
    @Test void windowsExporterIsDetectedAndProducesComparableReadings() {
        var samples = NodeMetricSource.parse("""
                windows_exporter_build_info{version="0.31.8",goarch="amd64"} 1
                windows_os_hostname{hostname="LAPTOP"} 1
                windows_cpu_time_total{core="0,0",mode="idle"} 70
                windows_cpu_time_total{core="0,0",mode="user"} 20
                windows_cpu_time_total{core="0,0",mode="privileged"} 10
                windows_cpu_time_total{core="0,0",mode="dpc"} 5
                windows_logical_disk_size_bytes{volume="C:"} 100
                windows_logical_disk_free_bytes{volume="C:"} 40
                windows_net_bytes_received_total{nic="Wi-Fi"} 500
                windows_memory_physical_total_bytes 100
                windows_memory_available_bytes 25
                windows_system_boot_time_timestamp 1
                """);
        assertEquals(ExporterType.WINDOWS_EXPORTER, NodeMetricSource.exporterType(samples));
        var cpu = NodeMetricSource.readings("CPU_USAGE", samples, ExporterType.WINDOWS_EXPORTER);
        assertEquals(1, cpu.size());
        assertEquals("0,0", cpu.get(0).key());
        assertEquals(100D, cpu.get(0).counter());
        assertEquals(70D, cpu.get(0).auxiliary());
        assertEquals(60D, NodeMetricSource.readings("DISK_USAGE", samples, ExporterType.WINDOWS_EXPORTER).get(0).value());
        assertEquals("Wi-Fi", NodeMetricSource.readings("NETWORK_RECEIVE", samples, ExporterType.WINDOWS_EXPORTER).get(0).key());
        assertEquals(75D, NodeMetricSource.readings("MEMORY_USAGE", samples, ExporterType.WINDOWS_EXPORTER).get(0).value());
        assertFalse(NodeMetricSource.readings("UPTIME", samples, ExporterType.WINDOWS_EXPORTER).isEmpty());
        assertTrue(NodeMetricSource.readings("LOAD_1M", samples, ExporterType.WINDOWS_EXPORTER).isEmpty());
    }
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
