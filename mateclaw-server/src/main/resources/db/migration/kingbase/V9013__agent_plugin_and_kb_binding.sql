-- V9013: Ensure all agent binding/extension columns exist.
-- These were either added in V9001 (which may have been skipped by the
-- dev-v2 migration renumbering + FlywayRepairConfig.repair()) or are new
-- columns (plugin_key, knowledge_base_ids_json) that never had a migration.
-- Using DO $$ blocks with INFORMATION_SCHEMA guards makes this idempotent.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'template_id'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN template_id VARCHAR(128);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'template_version'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN template_version VARCHAR(64);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'template_category'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN template_category VARCHAR(64);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'template_domain'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN template_domain VARCHAR(128);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'profile_id'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN profile_id VARCHAR(128);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'capability_pack_id'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN capability_pack_id VARCHAR(128);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'template_metadata_json'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN template_metadata_json TEXT;
    END IF;
END $$;

-- New columns never covered by any prior migration.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'plugin_key'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN plugin_key VARCHAR(128);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'mate_agent' AND column_name = 'knowledge_base_ids_json'
    ) THEN
        ALTER TABLE mate_agent ADD COLUMN knowledge_base_ids_json TEXT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_agent_template_id ON mate_agent(template_id);
CREATE INDEX IF NOT EXISTS idx_agent_template_category ON mate_agent(template_category);
CREATE INDEX IF NOT EXISTS idx_agent_profile_id ON mate_agent(profile_id);
CREATE INDEX IF NOT EXISTS idx_agent_capability_pack_id ON mate_agent(capability_pack_id);
CREATE INDEX IF NOT EXISTS idx_agent_plugin_key ON mate_agent(plugin_key);
