package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name = "monitor_event_state", uniqueConstraints = @UniqueConstraint(name = "uk_event_state_object", columnNames = {"rule_id", "object_key"}))
public class MonitorEventState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long stateId;
    @Column(name = "rule_id", nullable = false) private Long ruleId;
    @Column(name = "object_key", nullable = false, length = 32) private String objectKey;
    @Column(nullable = false) private int breaches;
    @Column(nullable = false) private boolean active;
    private Long openedEventId;
    private LocalDateTime lastObservedAt;
}
