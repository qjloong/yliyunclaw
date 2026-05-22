-- Issue #50 follow-up: see h2/V70 for rationale. MySQL doesn't allow
-- DELETE with a subquery scanning the same table directly, so use
-- the LEFT JOIN + IS NULL pattern.

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                                        WHERE TABLE_SCHEMA = DATABASE()
                                            AND TABLE_NAME = 'mate_cron_job'
                                            AND COLUMN_NAME = 'workspace_id');
SET @stmt := IF(@col_exists = 0,
                                'ALTER TABLE mate_cron_job ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1',
                                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- Step 1: physically purge any deleted=1 rows.
DELETE FROM mate_cron_job WHERE deleted = 1;

-- Step 2: idempotent re-dedup against active rows only.
DELETE t FROM mate_cron_job t
LEFT JOIN (
    SELECT MIN(id) AS keep_id
    FROM mate_cron_job
    WHERE deleted = 0
    GROUP BY workspace_id, agent_id, name
) k ON t.id = k.keep_id
WHERE t.deleted = 0 AND k.keep_id IS NULL;
