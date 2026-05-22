SET @c := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mate_conversation'
    AND COLUMN_NAME = 'runtime_provider_id'
);
SET @s := IF(@c = 0,
  'ALTER TABLE mate_conversation ADD COLUMN runtime_provider_id VARCHAR(64) DEFAULT NULL AFTER runtime_mode',
  'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mate_conversation'
    AND COLUMN_NAME = 'runtime_model_name'
);
SET @s := IF(@c = 0,
  'ALTER TABLE mate_conversation ADD COLUMN runtime_model_name VARCHAR(128) DEFAULT NULL AFTER runtime_provider_id',
  'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;