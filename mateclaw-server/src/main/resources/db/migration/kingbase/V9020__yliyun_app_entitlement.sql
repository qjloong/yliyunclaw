ALTER TABLE mc_workspace_user ADD COLUMN IF NOT EXISTS app_key VARCHAR(64);
ALTER TABLE mc_workspace_user ADD COLUMN IF NOT EXISTS config_version INTEGER;
