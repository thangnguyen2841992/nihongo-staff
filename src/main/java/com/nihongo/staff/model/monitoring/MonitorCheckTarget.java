package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name = "monitor_check_target", uniqueConstraints = @UniqueConstraint(name = "uk_monitor_check_key", columnNames = "target_key"))
public class MonitorCheckTarget {
    @Id private Long vpsId;
    @Column(nullable = false, length = 64) private String targetKey;
    @Column(length = 2048) private String url;
    @Column(nullable = false) private int statusMin = 200;
    @Column(nullable = false) private int statusMax = 399;
}
