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
    public static final List<Definition> MYSQL_DEFAULTS = List.of(
            new Definition("MYSQL_UPTIME", "MySQL uptime", "seconds", null, 60),
            new Definition("MYSQL_THREADS_CONNECTED", "Kết nối đang mở", "connections", null, 30),
            new Definition("MYSQL_THREADS_RUNNING", "Luồng đang hoạt động", "threads", null, 30),
            new Definition("MYSQL_QUERIES_RATE", "Truy vấn mỗi giây", "queries/s", null, 30),
            new Definition("MYSQL_SLOW_QUERIES_RATE", "Truy vấn chậm mỗi giây", "queries/s", null, 60),
            new Definition("MYSQL_CONNECTIONS_RATE", "Kết nối mới mỗi giây", "connections/s", null, 30),
            new Definition("MYSQL_BYTES_RECEIVED_RATE", "Dữ liệu nhận mỗi giây", "bytes/s", null, 30),
            new Definition("MYSQL_BYTES_SENT_RATE", "Dữ liệu gửi mỗi giây", "bytes/s", null, 30));
    public static final List<Definition> KAFKA_DEFAULTS = List.of(
            new Definition("KAFKA_BROKER_COUNT", "Broker đang hoạt động", "brokers", null, 30),
            new Definition("KAFKA_TOPIC_COUNT", "Số topic", "topics", null, 60),
            new Definition("KAFKA_PARTITION_COUNT", "Tổng partition", "partitions", null, 60),
            new Definition("KAFKA_OFFLINE_PARTITIONS", "Partition mất leader", "partitions", null, 30),
            new Definition("KAFKA_UNDER_REPLICATED_PARTITIONS", "Partition thiếu replica", "partitions", null, 30),
            new Definition("KAFKA_CONSUMER_GROUP_COUNT", "Số consumer group", "groups", null, 60),
            new Definition("KAFKA_CONSUMER_LAG", "Consumer lag", "messages", "KAFKA_CONSUMER_GROUP", 30));
    public static final List<Definition> PORT_DEFAULTS = List.of(
            new Definition("PORT_AVAILABLE", "TCP khả dụng (1 = UP, 0 = DOWN)", "", null, 30),
            new Definition("PORT_RESPONSE_MS", "Thời gian kiểm tra TCP", "ms", null, 30));
    public static final List<Definition> WEB_DEFAULTS = List.of(
            new Definition("WEB_AVAILABLE", "Web khả dụng (1 = UP, 0 = DOWN)", "", null, 30),
            new Definition("WEB_RESPONSE_MS", "Thời gian phản hồi HTTP", "ms", null, 30),
            new Definition("WEB_STATUS_CODE", "HTTP status code (0 = không nhận phản hồi)", "", null, 30));
    private final MonitorMetricRepository metrics;
    private final MonitorVpsMetricRepository assignments;
    private final MonitorVpsRepository servers;
    private final org.springframework.context.ApplicationEventPublisher events;

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
        for (Definition definition : MYSQL_DEFAULTS) {
            if (metrics.existsByMetricCode(definition.code())) continue;
            MonitorMetric metric = new MonitorMetric();
            metric.setMetricCode(definition.code()); metric.setMetricName(definition.name()); metric.setUnit(definition.unit());
            metric.setObjectLevelYn(false); metric.setScheduleSeconds(definition.interval());
            metric.setTimeoutMs(5000); metric.setCollectorType("MYSQL_JDBC");
            metric.setValueType(definition.code().endsWith("_RATE") ? MetricValueType.RATE : MetricValueType.GAUGE);
            metric.setDefaultMetric(true); metrics.save(metric);
        }
        for (Definition definition : KAFKA_DEFAULTS) {
            if (metrics.existsByMetricCode(definition.code())) continue;
            MonitorMetric metric = new MonitorMetric();
            metric.setMetricCode(definition.code()); metric.setMetricName(definition.name()); metric.setUnit(definition.unit());
            metric.setObjectLevelYn(definition.type() != null); metric.setObjectType(definition.type());
            metric.setScheduleSeconds(definition.interval()); metric.setTimeoutMs(10000); metric.setCollectorType("KAFKA_ADMIN");
            metric.setValueType(MetricValueType.GAUGE); metric.setDefaultMetric(true); metrics.save(metric);
        }
        initializeChecks(PORT_DEFAULTS, "PORT_CHECK");
        initializeChecks(WEB_DEFAULTS, "WEB_CHECK");
        for (MonitorVps vps : servers.findAll()) bindDefaults(vps);
    }

    private void initializeChecks(List<Definition> definitions, String collector) {
        for (var definition : definitions) {
            if (metrics.existsByMetricCode(definition.code())) continue;
            var metric = new MonitorMetric(); metric.setMetricCode(definition.code()); metric.setMetricName(definition.name());
            metric.setUnit(definition.unit()); metric.setObjectLevelYn(false); metric.setScheduleSeconds(definition.interval());
            metric.setTimeoutMs(5000); metric.setCollectorType(collector); metric.setValueType(MetricValueType.GAUGE);
            metric.setDefaultMetric(true); metrics.save(metric);
        }
    }

    @Transactional
    public void bindDefaults(MonitorVps vps) {
        // One query for existing links instead of one exists-check per catalog metric.
        var bound = new java.util.HashSet<Long>();
        for (MonitorVpsMetric existing : assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vps.getVpsId()))
            bound.add(existing.getMetric().getMetricId());
        for (MonitorMetric metric : metrics.findAll()) {
            if (!Boolean.TRUE.equals(metric.getDefaultMetric()) || bound.contains(metric.getMetricId())) continue;
            String collector = switch (ExporterType.forVps(vps)) {
                case MYSQL_JDBC -> "MYSQL_JDBC";
                case KAFKA_ADMIN -> "KAFKA_ADMIN";
                case PORT_CHECK -> "PORT_CHECK";
                case WEB_CHECK -> "WEB_CHECK";
                default -> "NODE_EXPORTER";
            };
            if (!collector.equals(metric.getCollectorType())) continue;
            if (ExporterType.forVps(vps) == ExporterType.WINDOWS_EXPORTER && "LOAD_1M".equals(metric.getMetricCode())) continue;
            MonitorVpsMetric link = new MonitorVpsMetric(); link.setVps(vps); link.setMetric(metric);
            link.setNextCollectionAt(LocalDateTime.now(ZoneOffset.UTC)); assignments.save(link);
        }
        events.publishEvent(new CollectionScheduleChanged());
    }
}
