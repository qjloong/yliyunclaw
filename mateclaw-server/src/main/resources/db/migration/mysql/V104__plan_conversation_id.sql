SET @c := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mate_plan'
    AND COLUMN_NAME = 'conversation_id'
);
SET @s := IF(@c = 0,
  'ALTER TABLE mate_plan ADD COLUMN conversation_id VARCHAR(64) DEFAULT NULL AFTER agent_id',
  'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @i := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mate_plan'
    AND INDEX_NAME = 'idx_plan_conversation_status'
);
SET @s := IF(@i = 0,
  'CREATE INDEX idx_plan_conversation_status ON mate_plan(conversation_id, status, create_time)',
  'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;