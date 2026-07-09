-- V98: Persist template/profile/capability binding metadata on agent instances.

ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_category VARCHAR(64);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_domain VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS profile_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS capability_pack_id VARCHAR(128);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS template_metadata_json CLOB;

CREATE INDEX IF NOT EXISTS idx_agent_template_id ON mate_agent(template_id);
CREATE INDEX IF NOT EXISTS idx_agent_template_category ON mate_agent(template_category);
CREATE INDEX IF NOT EXISTS idx_agent_profile_id ON mate_agent(profile_id);
CREATE INDEX IF NOT EXISTS idx_agent_capability_pack_id ON mate_agent(capability_pack_id);
