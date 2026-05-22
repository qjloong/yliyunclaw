-- Issue #50: deduplicate accumulated cron jobs and prevent future duplicates
-- at the DB level.
--
-- Legacy install guard: some older builds shipped duplicate Flyway versions
-- (V62/V63), so the original cron-job workspace/working-directory migrations
-- may have been skipped on those databases even though newer code expects the
-- columns. Ensure the key column exists.
ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_cron_job_workspace ON mate_cron_job(workspace_id, deleted);

-- Legacy H2 upgrades can still expose stale metadata/statement planning in the
-- same migration after ADD COLUMN IF NOT EXISTS. Final dedup + unique-index
-- enforcement is intentionally deferred to a later backfill migration once the
-- schema is fully stabilized for this startup path.
