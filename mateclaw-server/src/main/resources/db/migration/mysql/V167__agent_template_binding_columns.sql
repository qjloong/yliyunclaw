ALTER TABLE mate_agent ADD COLUMN template_id VARCHAR(64) NULL;
ALTER TABLE mate_agent ADD COLUMN profile_id VARCHAR(64) NULL;
ALTER TABLE mate_agent ADD COLUMN capability_pack_id VARCHAR(64) NULL;
ALTER TABLE mate_agent ADD COLUMN plugin_key VARCHAR(128) NULL;
ALTER TABLE mate_agent ADD COLUMN template_metadata_json TEXT NULL;
ALTER TABLE mate_agent ADD COLUMN knowledge_base_ids_json TEXT NULL;
