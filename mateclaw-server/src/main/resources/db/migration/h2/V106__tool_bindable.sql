ALTER TABLE mate_tool ADD COLUMN IF NOT EXISTS bindable BOOLEAN;

UPDATE mate_tool
SET bindable = CASE
    WHEN tool_type <> 'builtin' THEN TRUE
    WHEN icon IS NOT NULL AND TRIM(icon) <> '' THEN TRUE
    WHEN display_name IS NOT NULL AND TRIM(display_name) <> '' AND display_name <> name THEN TRUE
    ELSE FALSE
END
WHERE bindable IS NULL;

ALTER TABLE mate_tool ALTER COLUMN bindable SET DEFAULT TRUE;
ALTER TABLE mate_tool ALTER COLUMN bindable SET NOT NULL;