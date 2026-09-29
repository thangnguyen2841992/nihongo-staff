-- Optional when Hibernate ddl-auto=update is disabled; select the staff schema first.
-- Safe to rerun on MySQL: create the index only if it does not exist.
SET @event_index_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'monitor_event'
              AND index_name = 'idx_monitor_event_time'),
    'SELECT 1',
    'CREATE INDEX idx_monitor_event_time ON monitor_event (vps_id, collected_at, event_id)'
);
PREPARE event_index_statement FROM @event_index_ddl;
EXECUTE event_index_statement;
DEALLOCATE PREPARE event_index_statement;
