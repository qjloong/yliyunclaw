-- V92: add conversation-level runtime mode for chat plan-mode override
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_conversation' AND COLUMN_NAME = 'runtime_mode');
SET @s := IF(@c = 0, 'ALTER TABLE mate_conversation ADD COLUMN runtime_mode VARCHAR(32) NOT NULL DEFAULT ''default'' AFTER working_directory', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;