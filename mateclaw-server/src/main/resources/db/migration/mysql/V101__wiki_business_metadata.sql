-- RFC Teacher KB foundation: add generic business metadata to knowledge bases and raw materials.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND COLUMN_NAME = 'kb_kind');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_knowledge_base ADD COLUMN kb_kind VARCHAR(32) NOT NULL DEFAULT ''general''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND COLUMN_NAME = 'domain_profile_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_knowledge_base ADD COLUMN domain_profile_id VARCHAR(128) DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND INDEX_NAME = 'idx_wiki_kb_kind');
SET @s := IF(@c = 0, 'CREATE INDEX idx_wiki_kb_kind ON mate_wiki_knowledge_base(kb_kind)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base' AND INDEX_NAME = 'idx_wiki_kb_domain_profile');
SET @s := IF(@c = 0, 'CREATE INDEX idx_wiki_kb_domain_profile ON mate_wiki_knowledge_base(domain_profile_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_raw_material' AND COLUMN_NAME = 'material_type');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_raw_material ADD COLUMN material_type VARCHAR(64) NOT NULL DEFAULT ''general''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_raw_material' AND COLUMN_NAME = 'material_metadata_json');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_raw_material ADD COLUMN material_metadata_json TEXT DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_raw_material' AND INDEX_NAME = 'idx_wiki_raw_material_type');
SET @s := IF(@c = 0, 'CREATE INDEX idx_wiki_raw_material_type ON mate_wiki_raw_material(material_type)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;