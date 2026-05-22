-- V98: Persist template/profile/capability binding metadata on agent instances.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'template_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN template_id VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'template_version');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN template_version VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'template_category');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN template_category VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'template_domain');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN template_domain VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'profile_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN profile_id VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'capability_pack_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN capability_pack_id VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND COLUMN_NAME = 'template_metadata_json');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN template_metadata_json LONGTEXT', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND INDEX_NAME = 'idx_agent_template_id');
SET @s := IF(@c = 0, 'CREATE INDEX idx_agent_template_id ON mate_agent(template_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND INDEX_NAME = 'idx_agent_template_category');
SET @s := IF(@c = 0, 'CREATE INDEX idx_agent_template_category ON mate_agent(template_category)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND INDEX_NAME = 'idx_agent_profile_id');
SET @s := IF(@c = 0, 'CREATE INDEX idx_agent_profile_id ON mate_agent(profile_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent' AND INDEX_NAME = 'idx_agent_capability_pack_id');
SET @s := IF(@c = 0, 'CREATE INDEX idx_agent_capability_pack_id ON mate_agent(capability_pack_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
