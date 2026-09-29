-- Existing installations: run before starting the new version if severity is a MySQL ENUM.
-- Widen storage first, preserving old values, then normalize the legacy INFO level.
ALTER TABLE monitor_event_rule MODIFY COLUMN severity VARCHAR(10) NOT NULL;
ALTER TABLE monitor_event MODIFY COLUMN severity VARCHAR(10) NOT NULL;
UPDATE monitor_event_rule SET severity = 'MINOR' WHERE severity = 'INFO';
UPDATE monitor_event SET severity = 'MINOR' WHERE severity = 'INFO';
