package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitor_perf_baseline", uniqueConstraints = @UniqueConstraint(columnNames = {"vps_metric_id", "object_key"}))
@Getter @Setter
public class MonitorPerfBaseline {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long baselineId;
    @Column(nullable = false) private Long vpsMetricId;
    @Column(nullable = false, length = 255) private String objectKey;
    private Double counterValue;
    private Double auxiliaryValue;
    private LocalDateTime observedAt;
}
