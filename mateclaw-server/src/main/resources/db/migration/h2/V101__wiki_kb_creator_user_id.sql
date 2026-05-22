-- Track which user created a knowledge base so creators can delete their own KBs.

ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS creator_user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_wiki_kb_creator_user
    ON mate_wiki_knowledge_base(creator_user_id);