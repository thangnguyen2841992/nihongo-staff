-- Apply before deploying staff-service when schema validation is enabled.
-- Expand the MariaDB/MySQL enum while preserving every existing target type.
ALTER TABLE monitor_vps MODIFY COLUMN exporter_type
    ENUM('NODE_EXPORTER','WINDOWS_EXPORTER','MYSQL_JDBC','KAFKA_ADMIN','PORT_CHECK','WEB_CHECK') NULL;
CREATE TABLE IF NOT EXISTS monitor_check_target (
    vps_id BIGINT NOT NULL PRIMARY KEY,
    target_key VARCHAR(64) NOT NULL,
    url VARCHAR(2048) NULL,
    status_min INT NOT NULL DEFAULT 200,
    status_max INT NOT NULL DEFAULT 399,
    CONSTRAINT uk_monitor_check_key UNIQUE (target_key),
    CONSTRAINT fk_monitor_check_target_vps FOREIGN KEY (vps_id) REFERENCES monitor_vps(vps_id)
);
-- MetricCatalog creates metric definitions/assignments on startup.
