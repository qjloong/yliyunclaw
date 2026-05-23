-- Track which user created a knowledge base so creators can delete their own KBs.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND COLUMN_NAME = 'creator_user_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_knowledge_base ADD COLUMN creator_user_id BIGINT', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND INDEX_NAME = 'idx_wiki_kb_creator_user');
SET @s := IF(@c = 0, 'CREATE INDEX idx_wiki_kb_creator_user ON mate_wiki_knowledge_base(creator_user_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;