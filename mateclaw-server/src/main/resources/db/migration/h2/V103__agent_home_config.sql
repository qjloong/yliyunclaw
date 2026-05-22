ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS home_subtitle VARCHAR(512);
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS home_quick_starts_json CLOB;
