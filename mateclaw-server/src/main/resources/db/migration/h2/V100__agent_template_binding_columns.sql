ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_id VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS profile_id VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS capability_pack_id VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS plugin_key VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_metadata_json TEXT;
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS knowledge_base_ids_json TEXT;
