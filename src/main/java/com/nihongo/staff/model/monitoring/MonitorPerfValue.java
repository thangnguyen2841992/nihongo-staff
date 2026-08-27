package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Entity
@Table(
        name = "monitor_perf_value",
        indexes = {
                @Index(
                        name = "idx_perf_vps_metric_time",
                        columnList = "vps_id, metric_id, collected_at"
                ),
                @Index(
                        name = "idx_perf_vps_metric_object_time",
                        columnList = "vps_id, metric_id, object_id, collected_at"
                )
        }
)
@Getter
@Setter
public class MonitorPerfValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "value_id")
    private Long valueId;

    @Column(
            name = "collected_at",
            nullable = false
    )
    private LocalDateTime collectedAt;

    @Column(
            name = "vps_id",
            nullable = false
    )
    private Long vpsId;

    @Column(
            name = "metric_id",
            nullable = false
    )
    private Long metricId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(
            name = "value",
            nullable = false
    )
    private Double value;
}