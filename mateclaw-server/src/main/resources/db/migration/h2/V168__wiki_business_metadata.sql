-- RFC Teacher KB foundation: add generic business metadata to knowledge bases and raw materials.

ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS kb_kind VARCHAR(32) DEFAULT 'general';
ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS domain_profile_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wiki_kb_kind
    ON mate_wiki_knowledge_base(kb_kind);

CREATE INDEX IF NOT EXISTS idx_wiki_kb_domain_profile
    ON mate_wiki_knowledge_base(domain_profile_id);

ALTER TABLE mate_wiki_raw_material ADD COLUMN IF NOT EXISTS material_type VARCHAR(64) DEFAULT 'general';
ALTER TABLE mate_wiki_raw_material ADD COLUMN IF NOT EXISTS material_metadata_json CLOB;

CREATE INDEX IF NOT EXISTS idx_wiki_raw_material_type
    ON mate_wiki_raw_material(material_type);