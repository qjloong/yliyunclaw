-- RFC Teacher KB route tags: add page-level route tag cache for retrieval and routing.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS route_tags_json CLOB;
