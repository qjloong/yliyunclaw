-- V9009: 工作区项目级访问权限（dev1 定制，MetaY）
CREATE TABLE IF NOT EXISTS mate_workspace_project_permission (
  id BIGINT PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  member_user_id BIGINT,
  project_path VARCHAR(512),
  access_level VARCHAR(32),
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  deleted INTEGER
);
CREATE INDEX IF NOT EXISTS idx_ws_pp_ws ON mate_workspace_project_permission(workspace_id);
