-- Labelled listening tracks per lesson (e.g. Minna no Nihongo: ことば, 文型, 例文, 練習 A1 … C3).
-- Required before deploying to production (ddl-auto: validate). Local profiles create it automatically (ddl-auto: update).
CREATE TABLE IF NOT EXISTS lesson_audio_track (
  track_id        BIGINT       NOT NULL AUTO_INCREMENT,
  lesson_id       BIGINT       NOT NULL,
  position        INT          NOT NULL,
  label           VARCHAR(120) NOT NULL,
  audio_import_id VARCHAR(36)  NOT NULL,
  audio_asset_id  VARCHAR(36)  NOT NULL,
  PRIMARY KEY (track_id),
  KEY idx_lesson_audio_track_lesson (lesson_id, position)
);
