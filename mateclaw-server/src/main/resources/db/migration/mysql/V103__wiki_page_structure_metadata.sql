-- RFC Teacher KB canonical slicing: add page-level structure metadata cache.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_page' AND COLUMN_NAME = 'structure_metadata_json');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_page ADD COLUMN structure_metadata_json TEXT DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
