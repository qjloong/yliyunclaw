ALTER TABLE mate_tool
    ADD COLUMN IF NOT EXISTS bindable TINYINT(1) NULL AFTER enabled;

UPDATE mate_tool
SET bindable = CASE
    WHEN tool_type <> 'builtin' THEN 1
    WHEN icon IS NOT NULL AND TRIM(icon) <> '' THEN 1
    WHEN display_name IS NOT NULL AND TRIM(display_name) <> '' AND display_name <> name THEN 1
    ELSE 0
END
WHERE bindable IS NULL;

ALTER TABLE mate_tool
    MODIFY COLUMN bindable TINYINT(1) NOT NULL DEFAULT 1 AFTER enabled;