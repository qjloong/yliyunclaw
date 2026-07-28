-- V9012: 云盘用户映射表 + 映射列（yliyun 集成，MetaY）
CREATE TABLE IF NOT EXISTS mc_workspace_user (
    id               BIGINT       NOT NULL PRIMARY KEY,
    workspace_id     BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    role             VARCHAR(32)  NOT NULL DEFAULT 'member',
    yliyun_user_id   VARCHAR(64),
    yliyun_tenant_id VARCHAR(64),
    create_time      TIMESTAMP    NOT NULL,
    update_time      TIMESTAMP    NOT NULL,
    deleted          INT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_mc_ws_user_workspace ON mc_workspace_user (workspace_id);
CREATE INDEX IF NOT EXISTS idx_mc_ws_user_user ON mc_workspace_user (user_id);
CREATE INDEX IF NOT EXISTS idx_mc_ws_user_yliyun ON mc_workspace_user (yliyun_user_id);
