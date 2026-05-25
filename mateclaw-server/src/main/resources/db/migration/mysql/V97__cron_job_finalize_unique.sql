-- Final cron-job dedup + uniqueness enforcement for legacy MySQL upgrades.

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mate_cron_job'
                      AND COLUMN_NAME = 'workspace_id');
SET @stmt := IF(@col_exists = 0,
                'ALTER TABLE mate_cron_job ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @wd_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'mate_cron_job'
                     AND COLUMN_NAME = 'working_directory');
SET @stmt := IF(@wd_exists = 0,
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

DELETE FROM mate_cron_job WHERE deleted = 1;

DELETE t FROM mate_cron_job t
LEFT JOIN (
    SELECT MIN(id) AS keep_id
    FROM mate_cron_job
    WHERE deleted = 0
    GROUP BY workspace_id, agent_id, name
) k ON t.id = k.keep_id
WHERE t.deleted = 0 AND k.keep_id IS NULL;

SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mate_cron_job'
                      AND INDEX_NAME = 'uk_cron_job_workspace_agent_name');
SET @stmt := IF(@idx_exists = 0,
                'CREATE UNIQUE INDEX uk_cron_job_workspace_agent_name ON mate_cron_job(workspace_id, agent_id, name)',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
