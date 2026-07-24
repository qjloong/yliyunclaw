-- V9008: 工作区策略（dev1 定制，MetaY）
CREATE TABLE IF NOT EXISTS mate_workspace_policy (
  id BIGINT PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  sandbox_mode VARCHAR(32),
  approval_policy VARCHAR(32),
  network_policy VARCHAR(32),
  allowed_actions_json TEXT,
  denied_actions_json TEXT,
  risk_overrides_json TEXT,
  create_time DATETIME,
  update_time DATETIME,
  deleted INT
);
CREATE INDEX IF NOT EXISTS idx_ws_policy_ws ON mate_workspace_policy(workspace_id);
