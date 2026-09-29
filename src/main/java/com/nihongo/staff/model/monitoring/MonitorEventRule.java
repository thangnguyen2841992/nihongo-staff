package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name = "monitor_event_rule", indexes = @Index(name = "idx_event_rule_target", columnList = "vps_id,metric_id,enabled"))
public class MonitorEventRule {
    public enum Operator {
        GT, GTE, LT, LTE;
        public boolean matches(double value, double threshold) {
            return switch (this) {
                case GT -> value > threshold;
                case GTE -> value >= threshold;
                case LT -> value < threshold;
                case LTE -> value <= threshold;
            };
        }
    }
    public enum Severity { MINOR, WARNING, CRITICAL, FATAL }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;
    @Column(name = "vps_id", nullable = false) private Long vpsId;
    @Column(name = "metric_id", nullable = false) private Long metricId;
    @Column(nullable = false, length = 100) private String name;
    // "all" includes future objects; "vps" identifies a whole-server metric.
    @Column(nullable = false, length = 32) private String objectKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Operator operator;
    @Column(nullable = false) private Double threshold;
    @Convert(converter = EventSeverityConverter.class) @Column(nullable = false, length = 10) private Severity severity;
    @Column(nullable = false) private Integer consecutiveSamples;
    @Column(nullable = false) private Boolean enabled;
}
