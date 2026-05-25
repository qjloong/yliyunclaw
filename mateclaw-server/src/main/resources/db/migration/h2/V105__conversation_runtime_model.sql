ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS runtime_provider_id VARCHAR(64);
ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS runtime_model_name VARCHAR(128);