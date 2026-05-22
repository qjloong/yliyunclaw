-- V99: Stable external key for template-declared knowledge bindings.

ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS external_key VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wiki_kb_external_key
    ON mate_wiki_knowledge_base(external_key);
