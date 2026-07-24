-- V9010: 补齐 plugin_key 列（首次合并遗漏的迁移，MetaY）
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS plugin_key VARCHAR(64);
