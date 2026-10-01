CREATE TABLE IF NOT EXISTS monitor_mysql_target (
    vps_id BIGINT NOT NULL PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    password_encrypted VARCHAR(2048) NOT NULL,
    ssl_mode VARCHAR(20) NOT NULL,
    CONSTRAINT fk_monitor_mysql_target_vps
        FOREIGN KEY (vps_id) REFERENCES monitor_vps (vps_id)
);

-- Metric definitions and assignments are created by MetricCatalog on startup.
