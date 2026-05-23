-- V99: Stable external key for template-declared knowledge bindings.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND COLUMN_NAME = 'external_key');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_knowledge_base ADD COLUMN external_key VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND INDEX_NAME = 'idx_wiki_kb_external_key');
SET @s := IF(@c = 0, 'CREATE INDEX idx_wiki_kb_external_key ON mate_wiki_knowledge_base(external_key)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
