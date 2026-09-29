-- MySQL: additive schema only. Hibernate ddl-auto=update also creates these tables.
-- Severity values: MINOR, WARNING, CRITICAL, FATAL. For existing ENUM columns see monitor-event-severities.sql.
CREATE TABLE IF NOT EXISTS monitor_event_rule (
    rule_id BIGINT NOT NULL AUTO_INCREMENT,
    vps_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    object_key VARCHAR(32) NOT NULL,
    operator VARCHAR(10) NOT NULL,
    threshold DOUBLE NOT NULL,
    severity VARCHAR(10) NOT NULL,
    consecutive_samples INT NOT NULL,
    enabled BIT NOT NULL,
    PRIMARY KEY (rule_id),
    INDEX idx_event_rule_target (vps_id, metric_id, enabled)
);
CREATE TABLE IF NOT EXISTS monitor_event_state (
    state_id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    object_key VARCHAR(32) NOT NULL,
    breaches INT NOT NULL DEFAULT 0,
    active BIT NOT NULL DEFAULT 0,
    opened_event_id BIGINT NULL,
    last_observed_at DATETIME(6) NULL,
    PRIMARY KEY (state_id),
    CONSTRAINT uk_event_state_object UNIQUE (rule_id, object_key)
);
CREATE TABLE IF NOT EXISTS monitor_event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    vps_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(32) NOT NULL,
    object_name VARCHAR(255) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    operator VARCHAR(10) NOT NULL,
    threshold DOUBLE NOT NULL,
    perf_value DOUBLE NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    opened_event_id BIGINT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_monitor_event_history (vps_id, metric_id, event_id)
);
