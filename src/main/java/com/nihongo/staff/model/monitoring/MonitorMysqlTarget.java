package com.nihongo.staff.model.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "monitor_mysql_target")
@Getter
@Setter
public class MonitorMysqlTarget {
    @Id
    @Column(name = "vps_id")
    private Long vpsId;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "password_encrypted", nullable = false, length = 2048)
    private String passwordEncrypted;

    @Column(name = "ssl_mode", nullable = false, length = 20)
    private String sslMode;
}
