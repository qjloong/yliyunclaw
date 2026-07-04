-- RFC Teacher KB route tags: add page-level route tag cache for retrieval and routing.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_page' AND COLUMN_NAME = 'route_tags_json');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_page ADD COLUMN route_tags_json TEXT DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
