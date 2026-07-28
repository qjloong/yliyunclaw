-- Ensure all agent binding/extension columns exist.
-- These were either added in V9001 (which may have been skipped by the
-- dev-v2 migration renumbering + FlywayRepairConfig.repair()) or are new
-- columns (plugin_key, knowledge_base_ids_json) that never had a migration.
-- Using IF NOT EXISTS keeps this idempotent and safe to re-run.

ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_category VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_domain VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS profile_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS capability_pack_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_metadata_json CLOB;

-- New columns never covered by any prior migration.
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS plugin_key VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS knowledge_base_ids_json CLOB;

CREATE INDEX IF NOT EXISTS idx_agent_template_id ON mate_agent(template_id);
CREATE INDEX IF NOT EXISTS idx_agent_template_category ON mate_agent(template_category);
CREATE INDEX IF NOT EXISTS idx_agent_profile_id ON mate_agent(profile_id);
CREATE INDEX IF NOT EXISTS idx_agent_capability_pack_id ON mate_agent(capability_pack_id);
CREATE INDEX IF NOT EXISTS idx_agent_plugin_key ON mate_agent(plugin_key);
