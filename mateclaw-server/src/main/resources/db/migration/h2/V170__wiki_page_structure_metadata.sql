-- RFC Teacher KB canonical slicing: add page-level structure metadata cache.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS structure_metadata_json CLOB;
