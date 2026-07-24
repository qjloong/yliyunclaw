-- V9011: 补齐 knowledge_base_ids_json 列（首次合并遗漏，MetaY）
ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS knowledge_base_ids_json CLOB;
