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
    deleted                  INT          NOT NULL DEFAULT 0,
    INDEX idx_ws_invite_workspace (workspace_id),
    INDEX idx_ws_invite_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
