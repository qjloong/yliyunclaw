-- Backfill for legacy installs that missed cron-job column migrations due to
-- earlier duplicate Flyway version numbers.

ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS working_directory VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_cron_job_workspace ON mate_cron_job(workspace_id, deleted);