-- Final cron-job dedup + uniqueness enforcement for legacy H2 upgrades.
--
-- Some older installations skipped the original workspace/working-directory
-- migrations due to duplicate Flyway version numbers. V69/V70/V96 backfill the
-- missing schema safely; this migration performs the final data convergence and
-- installs the unique index once the schema is guaranteed to exist.

ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS working_directory VARCHAR(1024);
CREATE INDEX IF NOT EXISTS idx_cron_job_workspace ON mate_cron_job(workspace_id, deleted);

DELETE FROM mate_cron_job WHERE deleted = 1;

EXECUTE IMMEDIATE 'DELETE FROM mate_cron_job WHERE deleted = 0 AND id NOT IN (SELECT keep_id FROM (SELECT MIN(id) AS keep_id FROM mate_cron_job WHERE deleted = 0 GROUP BY workspace_id, agent_id, name) keepers)';

EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX IF NOT EXISTS uk_cron_job_workspace_agent_name ON mate_cron_job(workspace_id, agent_id, name)';
