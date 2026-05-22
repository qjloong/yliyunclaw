ALTER TABLE mate_cron_job_run ADD COLUMN IF NOT EXISTS execution_summary_status VARCHAR(64);
ALTER TABLE mate_cron_job_run ADD COLUMN IF NOT EXISTS execution_summary_text VARCHAR(1000);
