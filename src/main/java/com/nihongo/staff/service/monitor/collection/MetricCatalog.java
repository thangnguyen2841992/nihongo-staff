package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service @RequiredArgsConstructor
public class MetricCatalog {
    public record Definition(String code, String name, String unit, String type, int interval) {}
    public static final List<Definition> DEFAULTS = List.of(
            new Definition("CPU_USAGE", "CPU usage", "%", "CPU", 30),
            new Definition("MEMORY_USAGE", "Memory usage", "%", null, 60),
            new Definition("DISK_USAGE", "Filesystem usage", "%", "FILESYSTEM", 60),
            new Definition("NETWORK_RECEIVE", "Network receive", "bytes/s", "NETWORK", 30),
            new Definition("NETWORK_TRANSMIT", "Network transmit", "bytes/s", "NETWORK", 30),
            new Definition("LOAD_1M", "Load average (1 phút)", "", null, 60),
            new Definition("UPTIME", "Uptime", "seconds", null, 60));
    private final MonitorMetricRepository metrics;
    private final MonitorVpsMetricRepository assignments;
    private final MonitorVpsRepository servers;

    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(0)
    @Transactional
    public void initialize() {
        for (Definition definition : DEFAULTS) {
            if (metrics.existsByMetricCode(definition.code())) continue;
            MonitorMetric metric = new MonitorMetric();
            metric.setMetricCode(definition.code()); metric.setMetricName(definition.name()); metric.setUnit(definition.unit());
            metric.setObjectLevelYn(definition.type() != null); metric.setObjectType(definition.type());
            metric.setScheduleSeconds(definition.interval()); metric.setTimeoutMs(5000); metric.setCollectorType("NODE_EXPORTER");
            metric.setValueType(definition.code().startsWith("NETWORK_") ? MetricValueType.RATE : MetricValueType.GAUGE);
            metric.setDefaultMetric(true); metrics.save(metric);
        }
        for (MonitorVps vps : servers.findAll()) bindDefaults(vps);
    }

    @Transactional
    public void bindDefaults(MonitorVps vps) {
        for (MonitorMetric metric : metrics.findAll()) {
            if (!Boolean.TRUE.equals(metric.getDefaultMetric()) || assignments.existsByVps_VpsIdAndMetric_MetricId(vps.getVpsId(), metric.getMetricId())) continue;
            if (ExporterType.forVps(vps) == ExporterType.WINDOWS_EXPORTER && "LOAD_1M".equals(metric.getMetricCode())) continue;
            MonitorVpsMetric link = new MonitorVpsMetric(); link.setVps(vps); link.setMetric(metric);
            link.setNextCollectionAt(LocalDateTime.now(ZoneOffset.UTC)); assignments.save(link);
        }
    }
}
