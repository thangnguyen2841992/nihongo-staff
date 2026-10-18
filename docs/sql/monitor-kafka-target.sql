ALTER TABLE monitor_vps MODIFY COLUMN ip_address VARCHAR(255) NOT NULL;

CREATE TABLE IF NOT EXISTS monitor_kafka_target (
    vps_id BIGINT NOT NULL PRIMARY KEY,
    bootstrap_servers VARCHAR(1000) NOT NULL,
    security_protocol VARCHAR(20) NOT NULL,
    sasl_mechanism VARCHAR(30) NULL,
    username VARCHAR(256) NULL,
    password_encrypted VARCHAR(2048) NULL,
    CONSTRAINT uk_monitor_kafka_bootstrap UNIQUE (bootstrap_servers),
    CONSTRAINT fk_monitor_kafka_target_vps
        FOREIGN KEY (vps_id) REFERENCES monitor_vps (vps_id)
);

-- Metric definitions and target assignments are created by MetricCatalog on startup.
-- Production uses ddl-auto=validate, so apply this migration before deploying staff-service.
