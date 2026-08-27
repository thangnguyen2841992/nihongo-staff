package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "monitor_metric",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_metric_code",
                        columnNames = "metric_code"
                )
        },
        indexes = {
                @Index(
                        name = "idx_metric_enabled",
                        columnList = "enabled"
                )
        }
)
@Getter
@Setter
public class MonitorMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;


    /**
     * Mã duy nhất của metric.
     * Ví dụ:
     * CPU_USAGE
     * CPU_SYSTEM
     * MEMORY_USAGE
     */
    @Column(
            name = "metric_code",
            nullable = false,
            length = 100
    )
    private String metricCode;

    /**
     * Tên hiển thị trên UI.
     */
    @Column(
            name = "metric_name",
            nullable = false,
            length = 200
    )
    private String metricName;

    @Column(
            name = "description",
            length = 500
    )
    private String description;

    /**
     * N = Không có object
     * Y = Có object
     */
    @Column(
            name = "object_level_yn",
            nullable = false
    )
    private Boolean  objectLevelYn = false;

    /**
     * CPU / DISK / NETWORK / PROCESS...
     *
     * Chỉ sử dụng khi objectLevelYn = Y.
     */
    @Column(
            name = "object_type",
            length = 50
    )
    private String objectType;

    /**
     * Đơn vị:
     * %
     * MB
     * bytes/sec
     * ms
     * count
     * ...
     */
    @Column(
            name = "unit",
            length = 30
    )
    private String unit;

    /**
     * GAUGE / COUNTER / RATE
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "value_type",
            nullable = false,
            length = 20
    )
    private MetricValueType valueType = MetricValueType.GAUGE;

    /**
     * Collector dùng để lấy metric.
     *
     * PROMETHEUS
     * NODE_EXPORTER
     * JMX
     * CUSTOM
     * ...
     */
    @Column(
            name = "collector_type",
            nullable = false,
            length = 30
    )
    private String collectorType;

    /**
     * Timeout collection, milliseconds.
     */
    @Column(name = "timeout_ms")
    private Integer timeoutMs = 5000;

    /**
     * Chu kỳ thu thập, seconds.
     */
    @Column(
            name = "schedule_seconds",
            nullable = false
    )
    private Integer scheduleSeconds = 60;



    /**
     * true  = cho phép thu thập
     * false = không thu thập
     */
    @Column(
            name = "enabled",
            nullable = false
    )
    private Boolean enabled = true;

}