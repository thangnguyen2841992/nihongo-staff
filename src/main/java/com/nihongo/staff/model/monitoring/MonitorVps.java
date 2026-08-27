package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "monitor_vps",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_monitor_vps_hostname",
                        columnNames = "hostname"
                )
        }
)
@Getter
@Setter
public class MonitorVps extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vps_id")
    private Long vpsId;

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "agent_port")
    private Integer agentPort = 9100;

    @Column(name = "os_type", length = 50)
    private String osType;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "architecture", length = 50)
    private String architecture;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VpsStatus status = VpsStatus.UNKNOWN;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
