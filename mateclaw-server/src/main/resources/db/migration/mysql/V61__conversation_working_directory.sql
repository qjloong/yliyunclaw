-- V61: add conversation-level working directory override for session cwd binding
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_conversation' AND COLUMN_NAME = 'working_directory');
SET @s := IF(@c = 0, 'ALTER TABLE mate_conversation ADD COLUMN working_directory VARCHAR(1024) DEFAULT NULL AFTER workspace_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;