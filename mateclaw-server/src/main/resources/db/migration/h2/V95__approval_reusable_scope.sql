ALTER TABLE mate_tool_approval ADD COLUMN IF NOT EXISTS workspace_id BIGINT;
ALTER TABLE mate_tool_approval ADD COLUMN IF NOT EXISTS project_path VARCHAR(1024);
ALTER TABLE mate_tool_approval ADD COLUMN IF NOT EXISTS approval_key VARCHAR(64);
ALTER TABLE mate_tool_approval ADD COLUMN IF NOT EXISTS grant_scope VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tool_approval_grant_lookup
    ON mate_tool_approval(user_id, tool_name, approval_key, grant_scope);