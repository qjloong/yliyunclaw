-- Backfill for legacy installs that missed cron-job column migrations due to
-- earlier duplicate Flyway version numbers.

SET @col_workspace := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'mate_cron_job'
                         AND COLUMN_NAME = 'workspace_id');
SET @stmt := IF(@col_workspace = 0,
                'ALTER TABLE mate_cron_job ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_workdir := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'mate_cron_job'
                       AND COLUMN_NAME = 'working_directory');
SET @stmt := IF(@col_workdir = 0,
                'ALTER TABLE mate_cron_job ADD COLUMN working_directory VARCHAR(1024) NULL',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_ws_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'mate_cron_job'
                         AND INDEX_NAME = 'idx_cron_job_workspace');
SET @stmt := IF(@idx_ws_exists = 0,
                'CREATE INDEX idx_cron_job_workspace ON mate_cron_job(workspace_id, deleted)',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;