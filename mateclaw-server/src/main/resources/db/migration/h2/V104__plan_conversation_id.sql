ALTER TABLE mate_plan ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_plan_conversation_status ON mate_plan(conversation_id, status, create_time);