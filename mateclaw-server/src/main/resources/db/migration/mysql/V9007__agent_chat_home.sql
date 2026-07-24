-- V9007: 聊天首页配置（dev1 定制，MetaY）
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS home_subtitle VARCHAR(512);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS home_quick_starts_json TEXT;
