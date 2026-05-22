CREATE TABLE IF NOT EXISTS mate_workspace_invite (
    id                       BIGINT       NOT NULL PRIMARY KEY,
    workspace_id             BIGINT       NOT NULL,
    role                     VARCHAR(32)  NOT NULL,
    invited_by_user_id       BIGINT       NOT NULL,
    max_uses                 INT          NOT NULL DEFAULT 1,
    use_count                INT          NOT NULL DEFAULT 0,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'active',
    expires_at               DATETIME     NOT NULL,
    last_accepted_by_user_id BIGINT,
    last_accepted_time       DATETIME,
    create_time              DATETIME     NOT NULL,
    update_time              DATETIME     NOT NULL,
    deleted                  INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ws_invite_workspace ON mate_workspace_invite(workspace_id);
CREATE INDEX IF NOT EXISTS idx_ws_invite_status ON mate_workspace_invite(status);
