SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'knowledge_base_ids_json');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN knowledge_base_ids_json JSON NULL COMMENT ''Optional JSON array of wiki knowledge base ids bound to this agent''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
