package com.nihongo.staff.model.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "monitor_kafka_target", uniqueConstraints =
        @UniqueConstraint(name = "uk_monitor_kafka_bootstrap", columnNames = "bootstrap_servers"))
@Getter
@Setter
public class MonitorKafkaTarget {
    @Id
    @Column(name = "vps_id")
    private Long vpsId;

    @Column(name = "bootstrap_servers", nullable = false, length = 1000)
    private String bootstrapServers;

    @Column(name = "security_protocol", nullable = false, length = 20)
    private String securityProtocol;

    @Column(name = "sasl_mechanism", length = 30)
    private String saslMechanism;

    @Column(name = "username", length = 256)
    private String username;

    @Column(name = "password_encrypted", length = 2048)
    private String passwordEncrypted;
}
