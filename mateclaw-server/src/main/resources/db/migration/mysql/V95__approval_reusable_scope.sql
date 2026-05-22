SET @c_workspace := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mate_tool_approval'
      AND COLUMN_NAME = 'workspace_id'
);
SET @s_workspace := IF(@c_workspace = 0,
    'ALTER TABLE mate_tool_approval ADD COLUMN workspace_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt_workspace FROM @s_workspace;
EXECUTE stmt_workspace;
DEALLOCATE PREPARE stmt_workspace;

SET @c_project := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mate_tool_approval'
      AND COLUMN_NAME = 'project_path'
);
SET @s_project := IF(@c_project = 0,
    'ALTER TABLE mate_tool_approval ADD COLUMN project_path VARCHAR(1024) NULL',
    'SELECT 1');
PREPARE stmt_project FROM @s_project;
EXECUTE stmt_project;
DEALLOCATE PREPARE stmt_project;

SET @c_key := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mate_tool_approval'
      AND COLUMN_NAME = 'approval_key'
);
SET @s_key := IF(@c_key = 0,
    'ALTER TABLE mate_tool_approval ADD COLUMN approval_key VARCHAR(64) NULL',
    'SELECT 1');
PREPARE stmt_key FROM @s_key;
EXECUTE stmt_key;
DEALLOCATE PREPARE stmt_key;

SET @c_scope := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mate_tool_approval'
      AND COLUMN_NAME = 'grant_scope'
);
SET @s_scope := IF(@c_scope = 0,
    'ALTER TABLE mate_tool_approval ADD COLUMN grant_scope VARCHAR(32) NULL',
    'SELECT 1');
PREPARE stmt_scope FROM @s_scope;
EXECUTE stmt_scope;
DEALLOCATE PREPARE stmt_scope;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mate_tool_approval'
      AND INDEX_NAME = 'idx_tool_approval_grant_lookup'
);
SET @s_idx := IF(@idx_exists = 0,
    'CREATE INDEX idx_tool_approval_grant_lookup ON mate_tool_approval(user_id, tool_name, approval_key, grant_scope)',
    'SELECT 1');
PREPARE stmt_idx FROM @s_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;