package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "monitor_prometheus_target", uniqueConstraints = {@UniqueConstraint(name = "uk_monitor_prometheus_target", columnNames = {"job_name", "target"})})
@Getter
@Setter
public class MonitorPrometheusTarget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "target_id")
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vps_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prometheus_target_vps"))
    private MonitorVps vps;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName = "node";

    @Column(name = "target", nullable = false, length = 255)
    private String target;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
