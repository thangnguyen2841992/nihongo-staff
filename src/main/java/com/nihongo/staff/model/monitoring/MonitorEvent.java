package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name = "monitor_event", indexes = {
        @Index(name = "idx_monitor_event_history", columnList = "vps_id,metric_id,event_id"),
        @Index(name = "idx_monitor_event_time", columnList = "vps_id,collected_at,event_id")})
public class MonitorEvent {
    public enum Kind { ALERT, RECOVERY }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "event_id") private Long eventId;
    @Column(nullable = false) private Long ruleId;
    @Column(name = "vps_id", nullable = false) private Long vpsId;
    @Column(name = "metric_id", nullable = false) private Long metricId;
    @Column(nullable = false, length = 100) private String ruleName;
    @Column(nullable = false, length = 32) private String objectKey;
    @Column(nullable = false, length = 255) private String objectName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Kind kind;
    @Convert(converter = EventSeverityConverter.class) @Column(nullable = false, length = 10) private MonitorEventRule.Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MonitorEventRule.Operator operator;
    @Column(nullable = false) private Double threshold;
    @Column(nullable = false) private Double perfValue;
    @Column(nullable = false) private LocalDateTime collectedAt;
    // Recovery references its alert; snapshots survive editing/deleting the rule and perf retention.
    private Long openedEventId;
}
