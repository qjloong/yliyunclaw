SET @c1 := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_cron_job_run' AND COLUMN_NAME = 'execution_summary_status');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE mate_cron_job_run ADD COLUMN execution_summary_status VARCHAR(64) DEFAULT NULL AFTER delivery_error', 'SELECT 1');
PREPARE stmt1 FROM @s1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @c2 := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_cron_job_run' AND COLUMN_NAME = 'execution_summary_text');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE mate_cron_job_run ADD COLUMN execution_summary_text VARCHAR(1000) DEFAULT NULL AFTER execution_summary_status', 'SELECT 1');
PREPARE stmt2 FROM @s2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
