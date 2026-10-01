package com.nihongo.staff.service.monitor.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.vps.MonitorVpsService;
import com.nihongo.staff.model.monitoring.dto.RegisterMonitorVpsRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import com.nihongo.staff.service.monitor.metric.VpsMetricConfigService;
import com.nihongo.staff.service.monitor.vps.PrometheusTargetService;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:perf;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "monitoring.prometheus-targets-file=target/prometheus-test/node_targets.json", "monitoring.prometheus-windows-targets-file=target/prometheus-test/windows_targets.json"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PerfPersistenceTest.Config.class)
class PerfPersistenceTest {
    @Configuration @EntityScan("com.nihongo.staff.model") @EnableJpaRepositories("com.nihongo.staff.repository")
    @Import({MetricCatalog.class, PerfCollectionStore.class, VpsPerformanceService.class, VpsMetricConfigService.class, MonitorVpsService.class, PrometheusTargetService.class, com.nihongo.staff.service.monitor.event.MonitorEventService.class})
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean NodeMetricSource source() { return mock(NodeMetricSource.class); }
    }
    @Autowired MetricCatalog catalog;
    @Autowired PerfCollectionStore store;
    @Autowired VpsPerformanceService performance;
    @Autowired VpsMetricConfigService configuration;
    @Autowired MonitorVpsRepository servers;
    @Autowired MonitorVpsMetricRepository assignments;
    @Autowired MonitorPerfValueRepository values;
    @Autowired MonitorObjectRepository objects;
    @Autowired MonitorPrometheusTargetRepository targets;
    @Autowired PrometheusTargetService prometheus;
    @Autowired MonitorVpsService registration;
    @Autowired NodeMetricSource source;
    MonitorVps vps;
    @BeforeEach void setup() {
        catalog.initialize();
        vps = new MonitorVps(); vps.setHostname("test-host"); vps.setIpAddress("127.0.0.1"); vps.setAgentPort(9100); servers.save(vps); catalog.bindDefaults(vps);
    }
    MonitorVpsMetric link(String code) { return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vps.getVpsId()).stream().filter(a -> a.getMetric().getMetricCode().equals(code)).findFirst().orElseThrow(); }
    PerfCollectionStore.Job claim(String code) { var link = link(code); link.setNextCollectionAt(PerfCollectionStore.now().minusSeconds(1)); return store.claim(link.getVpsMetricId()); }

    @Test void registrationPersistsDefaultMetricAssignmentsAndDiscoveredObjectsTogether() {
        when(source.fetch("127.0.0.2", 9100, 5000)).thenReturn(NodeMetricSource.parse("node_cpu_seconds_total{cpu=\"0\",mode=\"idle\"} 10\nnode_network_receive_bytes_total{device=\"eth0\"} 100\n"));
        var request = new RegisterMonitorVpsRequest(); request.setIpAddress("127.0.0.2"); request.setAgentPort(9100); request.setHostname("new-host");
        var saved = registration.registerVps(request);
        assertEquals(7, assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(saved.getVpsId()).size());
        assertEquals(2, objects.findByVps_VpsId(saved.getVpsId()).size());
        assertEquals("127.0.0.2:9100", targets.findByVps_VpsIdAndJobName(saved.getVpsId(), "node").orElseThrow().getTarget());
        prometheus.sync();
        String targetFile = assertDoesNotThrow(() -> java.nio.file.Files.readString(
                java.nio.file.Path.of("target/prometheus-test/node_targets.json")));
        assertTrue(targetFile.contains("127.0.0.2:9100"));
        assertTrue(targetFile.contains("new-host"));
    }

    @Test void windowsRegistrationUsesWindowsCollectorAndTargetFile() {
        when(source.fetch("127.0.0.3", 9182, 5000)).thenReturn(NodeMetricSource.parse("""
                windows_exporter_build_info{version="0.31.8",goarch="amd64"} 1
                windows_os_hostname{hostname="LAPTOP"} 1
                windows_os_info{product="Windows 10",version="10.0"} 1
                windows_cpu_time_total{core="0,0",mode="idle"} 70
                windows_cpu_time_total{core="0,0",mode="user"} 30
                windows_logical_disk_size_bytes{volume="C:"} 100
                windows_logical_disk_free_bytes{volume="C:"} 40
                """));
        var discovery = registration.discover(new com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest("127.0.0.3", 9182));
        assertEquals(ExporterType.WINDOWS_EXPORTER, discovery.getExporterType());
        assertEquals("LAPTOP", discovery.getHostname());
        var request = new RegisterMonitorVpsRequest();
        request.setIpAddress("127.0.0.3");
        request.setAgentPort(9182);
        request.setHostname("stale-name");
        var saved = registration.registerVps(request);
        assertEquals(ExporterType.WINDOWS_EXPORTER, saved.getExporterType());
        assertEquals("LAPTOP", saved.getHostname());
        assertEquals("Windows", saved.getOsType());
        assertEquals(6, assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(saved.getVpsId()).size());
        assertEquals(2, objects.findByVps_VpsId(saved.getVpsId()).size());
        assertEquals("WINDOWS_EXPORTER", store.claim(assignments.findByVps_VpsIdAndMetric_MetricCode(
                saved.getVpsId(), "CPU_USAGE").orElseThrow().getVpsMetricId()).collector());
        assertEquals("127.0.0.3:9182", targets.findByVps_VpsIdAndJobName(saved.getVpsId(), "windows_vps").orElseThrow().getTarget());
        prometheus.sync();
        String windowsTargets = assertDoesNotThrow(() -> java.nio.file.Files.readString(
                java.nio.file.Path.of("target/prometheus-test/windows_targets.json")));
        assertTrue(windowsTargets.contains("127.0.0.3:9182"));
        String nodeTargets = assertDoesNotThrow(() -> java.nio.file.Files.readString(
                java.nio.file.Path.of("target/prometheus-test/node_targets.json")));
        assertFalse(nodeTargets.contains("127.0.0.3:9182"));
    }

    @Test void discoveryUsesTheSharedParserAndPreservesCustomPort() {
        when(source.fetch("127.0.0.2", 9200, 5000)).thenReturn(NodeMetricSource.parse(
                "node_exporter_build_info{version=\"1.9.0\"} 1\r\nnode_uname_info{nodename=\"fresh-host\",sysname=\"Linux\",release=\"6.8\",machine=\"x86_64\"} 1\r\n"));
        var request = new com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest(" 127.0.0.2 ", 9200);
        var result = registration.discover(request);
        assertTrue(result.isInstalled());
        assertEquals(9200, result.getPort());
        assertEquals("fresh-host", result.getHostname());
        assertEquals("1.9.0", result.getNodeExporterVersion());
    }

    @Test void failedDiscoveryKeepsTheRequestedPortAndDoesNotWriteVps() {
        long before = servers.count();
        when(source.fetch("127.0.0.2", 9201, 5000)).thenThrow(new IllegalStateException("Node Exporter timeout"));
        var result = registration.discover(new com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest("127.0.0.2", 9201));
        assertFalse(result.isInstalled());
        assertEquals(9201, result.getPort());
        assertEquals("Node Exporter timeout", result.getMessage());
        assertEquals(before, servers.count());
    }

    @Test void registrationUsesFreshExporterMetadataInsteadOfStaleDiscoveryFields() {
        when(source.fetch("127.0.0.4", 9100, 5000)).thenReturn(NodeMetricSource.parse(
                "node_uname_info{nodename=\"fresh-host\",sysname=\"Linux\",release=\"6.8\",machine=\"x86_64\"} 1\n"));
        var request = new RegisterMonitorVpsRequest();
        request.setIpAddress("127.0.0.4"); request.setHostname("outdated-name"); request.setOsType("outdated-os");
        var saved = registration.registerVps(request);
        assertEquals("fresh-host", saved.getHostname());
        assertEquals("Linux", saved.getOsType());
        assertEquals("6.8", saved.getOsVersion());
    }
    @Test void cpuWarmupThenDeltaIsPersistedPerObjectAndSurvivesReset() {
        var t = PerfCollectionStore.now().minusSeconds(30);
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 100D, 70D)), t);
        assertEquals(0, values.count());
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 200D, 120D)), t.plusSeconds(30));
        assertEquals(1, values.count()); assertEquals(50D, values.findAll().get(0).getValue());
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 10D, 5D)), t.plusSeconds(60));
        assertEquals(1, values.count());
    }
    @Test void ratesUsePersistedBaselineAndNeverMixDevices() {
        var t = PerfCollectionStore.now().minusSeconds(10);
        store.complete(claim("NETWORK_RECEIVE"), List.of(new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, 100D, null), new NodeMetricSource.Reading("NETWORK", "eth1", Map.of("device", "eth1"), null, 500D, null)), t);
        store.complete(claim("NETWORK_RECEIVE"), List.of(new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, 300D, null), new NodeMetricSource.Reading("NETWORK", "eth1", Map.of("device", "eth1"), null, 900D, null)), t.plusSeconds(10));
        var result = performance.read(vps.getVpsId(), "NETWORK_RECEIVE", null, null);
        assertEquals(2, result.objects().size()); assertEquals(20D, result.objects().get(0).points().get(0).value()); assertEquals(40D, result.objects().get(1).points().get(0).value());
    }
    @Test void leaseRejectsDuplicateWorkAndScheduleCanInheritDefault() {
        var job = claim("CPU_USAGE"); assertNotNull(job); assertNull(store.claim(job.id()));
        configuration.updateVps(vps.getVpsId(), "CPU_USAGE", new VpsMetricConfigService.Update(120, null, false));
        assertEquals(120, PerfCollectionStore.interval(link("CPU_USAGE")));
        configuration.updateVps(vps.getVpsId(), "CPU_USAGE", new VpsMetricConfigService.Update(null, null, true));
        assertEquals(30, PerfCollectionStore.interval(link("CPU_USAGE")));
    }
    @Test void historyReadsOnlyRequestedObjectAndOfflineObjectsKeepValues() {
        store.complete(claim("NETWORK_RECEIVE"), List.of(new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, 100D, null)), PerfCollectionStore.now().minusSeconds(10));
        store.complete(claim("NETWORK_RECEIVE"), List.of(new NodeMetricSource.Reading("NETWORK", "eth0", Map.of("device", "eth0"), null, 300D, null)), PerfCollectionStore.now());
        var id = objects.findByVps_VpsId(vps.getVpsId()).get(0).getObjectId().toString();
        assertEquals(1, performance.read(vps.getVpsId(), "NETWORK_RECEIVE", 1, id).objects().get(0).points().size());
        assertTrue(performance.read(vps.getVpsId(), "NETWORK_RECEIVE", 1, "not-an-object").objects().isEmpty());
        store.complete(claim("NETWORK_RECEIVE"), List.of(), PerfCollectionStore.now());
        var current = performance.read(vps.getVpsId(), "NETWORK_RECEIVE", null, null);
        assertEquals("OFFLINE", current.objects().get(0).status()); assertFalse(current.objects().get(0).points().isEmpty());
    }
    @Test void failedCollectionKeepsHistoryAndStoresError() {
        store.complete(claim("MEMORY_USAGE"), List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), 70D, null, null)), PerfCollectionStore.now());
        store.fail(claim("MEMORY_USAGE"), "Timeout");
        var result = performance.read(vps.getVpsId(), "MEMORY_USAGE", null, null);
        assertEquals("Timeout", result.collectionError()); assertEquals(70D, result.objects().get(0).points().get(0).value()); assertTrue(result.objects().get(0).stale());
    }
    @Test void rejectsInvalidScheduleAndTimeout() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> VpsMetricConfigService.validate(new VpsMetricConfigService.Update(0, 5000, true)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> VpsMetricConfigService.validate(new VpsMetricConfigService.Update(30, 50000, true)));
    }
    @Test void disabledAssignmentsAreNotClaimedAndOldLeaseCannotWrite() {
        var job = claim("MEMORY_USAGE");
        link("MEMORY_USAGE").setLeaseToken("replacement-token");
        store.complete(job, List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), 99D, null, null)), PerfCollectionStore.now());
        assertEquals(0, values.count());
        link("MEMORY_USAGE").setEnabled(false);
        assertNull(store.claim(job.id()));
    }
    @Test void registrationDoesNotSaveAnythingWhenObjectDiscoveryFails() {
        long before = servers.count();
        when(source.fetch("127.0.0.3", 9100, 5000)).thenThrow(new IllegalStateException("Timeout"));
        var request = new RegisterMonitorVpsRequest(); request.setIpAddress("127.0.0.3"); request.setHostname("unreachable"); request.setAgentPort(9100);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> registration.registerVps(request));
        assertEquals(before, servers.count());
    }
    @Test void nativeRetentionQueryDeletesOnlyExpiredValues() {
        store.complete(claim("MEMORY_USAGE"), List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), 70D, null, null)), PerfCollectionStore.now().minusDays(35));
        store.complete(claim("MEMORY_USAGE"), List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), 80D, null, null)), PerfCollectionStore.now());
        assertEquals(1, values.deleteExpired(PerfCollectionStore.now().minusDays(30)));
        assertEquals(1, values.count());
    }
    @Test void defaultScheduleChangesPreservePerVpsOverridesAndMetricDisablePausesReadout() {
        var cpu = link("CPU_USAGE");
        configuration.updateMetric(cpu.getMetric().getMetricId(), new VpsMetricConfigService.Update(90, 6000, true));
        assertEquals(90, configuration.configs(vps.getVpsId()).stream().filter(c -> c.code().equals("CPU_USAGE")).findFirst().orElseThrow().effectiveScheduleSeconds());
        configuration.updateVps(vps.getVpsId(), "CPU_USAGE", new VpsMetricConfigService.Update(120, null, true));
        configuration.updateMetric(cpu.getMetric().getMetricId(), new VpsMetricConfigService.Update(45, 5000, false));
        assertEquals(120, PerfCollectionStore.interval(cpu));
        assertEquals("PAUSED", performance.read(vps.getVpsId(), "CPU_USAGE", null, null).state());
    }
    @Test void repeatedFailuresBackOffAndOnlyChangedErrorsNeedLogging() {
        var first = claim("CPU_USAGE");
        assertTrue(store.fail(first, "Connection timeout"));
        var second = claim("CPU_USAGE");
        assertFalse(store.fail(second, "Connection timeout"));
        assertEquals(2, link("CPU_USAGE").getConsecutiveFailures());
        assertTrue(link("CPU_USAGE").getNextCollectionAt().isAfter(PerfCollectionStore.now().plusSeconds(100)));
        store.complete(claim("CPU_USAGE"), List.of(new NodeMetricSource.Reading("CPU", "0", Map.of("cpu", "0"), null, 100D, 50D)), PerfCollectionStore.now());
        assertEquals(0, link("CPU_USAGE").getConsecutiveFailures());
        assertNull(link("CPU_USAGE").getLastError());
    }
    @Test void tenMinuteHistoryExcludesOlderFutureAndOtherMetricSamples() {
        var memory = link("MEMORY_USAGE");
        var time = PerfCollectionStore.now();
        for (int offset : new int[]{-660, -540, -60, 60}) {
            var value = new MonitorPerfValue(); value.setVpsId(vps.getVpsId()); value.setMetricId(memory.getMetric().getMetricId());
            value.setCollectedAt(time.plusSeconds(offset)); value.setValue((double) Math.abs(offset)); values.save(value);
        }
        var wrongMetric = new MonitorPerfValue(); wrongMetric.setVpsId(vps.getVpsId()); wrongMetric.setMetricId(link("LOAD_1M").getMetric().getMetricId());
        wrongMetric.setCollectedAt(time.minusSeconds(60)); wrongMetric.setValue(999D); values.save(wrongMetric);
        var points = performance.read(vps.getVpsId(), "MEMORY_USAGE", null, "vps", 10).objects().get(0).points();
        assertEquals(List.of(540D, 60D), points.stream().map(VpsPerformanceService.Point::value).filter(java.util.Objects::nonNull).toList());
        assertEquals(time.minusSeconds(540).toEpochSecond(java.time.ZoneOffset.UTC), points.get(0).timestamp());
        assertTrue(points.stream().allMatch(p -> p.timestamp() >= time.minusMinutes(10).toEpochSecond(java.time.ZoneOffset.UTC) && p.timestamp() <= time.toEpochSecond(java.time.ZoneOffset.UTC)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> performance.read(vps.getVpsId(), "MEMORY_USAGE", null, "vps", 0));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> performance.read(vps.getVpsId(), "MEMORY_USAGE", 1, "vps", 10));
    }
}
