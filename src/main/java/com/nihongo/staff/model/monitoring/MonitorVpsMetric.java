package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "monitor_vps_metric",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vps_metric",
                        columnNames = {
                                "vps_id",
                                "metric_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_vps_metric_vps",
                        columnList = "vps_id"
                ),
                @Index(
                        name = "idx_vps_metric_metric",
                        columnList = "metric_id"
                ),
                @Index(
                        name = "idx_vps_metric_enabled",
                        columnList = "vps_id, enabled"
                )
        }
)
@Getter
@Setter
public class MonitorVpsMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vps_metric_id")
    private Long vpsMetricId;

    /**
     * VPS sử dụng metric này.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "vps_id",
            nullable = false
    )
    private MonitorVps vps;

    /**
     * Metric được áp dụng cho VPS.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "metric_id",
            nullable = false
    )
    private MonitorMetric metric;

    /**
     * Có thu thập metric này trên VPS hay không.
     */
    @Column(
            name = "enabled",
            nullable = false
    )
    private Boolean enabled = true;

    /**
     * Schedule riêng cho VPS.
     *
     * Nếu null -> sử dụng scheduleSeconds
     * của MonitorMetric.
     */
    @Column(name = "schedule_seconds")
    private Integer scheduleSeconds;

    /**
     * Thứ tự hiển thị trên UI.
     */
    @Column(name = "display_order")
    private Integer displayOrder;
}