CREATE TABLE IF NOT EXISTS mate_workspace_model_provider (
    id                              BIGINT       NOT NULL PRIMARY KEY,
    workspace_id                    BIGINT       NOT NULL,
    provider_id                     VARCHAR(64)  NOT NULL,
    name                            VARCHAR(128) NOT NULL,
    api_key_prefix                  VARCHAR(32),
    chat_model                      VARCHAR(64),
    api_key_encrypted               TEXT,
    base_url                        VARCHAR(512),
    generate_kwargs                 TEXT,
    is_custom                       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_local                        BOOLEAN      NOT NULL DEFAULT FALSE,
    support_model_discovery         BOOLEAN      NOT NULL DEFAULT FALSE,
    support_connection_check        BOOLEAN      NOT NULL DEFAULT FALSE,
    freeze_url                      BOOLEAN      NOT NULL DEFAULT FALSE,
    require_api_key                 BOOLEAN      NOT NULL DEFAULT TRUE,
    auth_type                       VARCHAR(16)  NOT NULL DEFAULT 'api_key',
    oauth_access_token_encrypted    TEXT,
    oauth_refresh_token_encrypted   TEXT,
    oauth_expires_at                BIGINT,
    oauth_account_id                VARCHAR(128),
    fallback_priority               INT          NOT NULL DEFAULT 0,
    enabled                         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time                     TIMESTAMP    NOT NULL,
    update_time                     TIMESTAMP    NOT NULL,
    CONSTRAINT uk_ws_model_provider UNIQUE (workspace_id, provider_id)
);
CREATE INDEX IF NOT EXISTS idx_ws_model_provider_workspace
    ON mate_workspace_model_provider(workspace_id);

CREATE TABLE IF NOT EXISTS mate_workspace_model_config (
    id                       BIGINT       NOT NULL PRIMARY KEY,
    workspace_id             BIGINT       NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    provider                 VARCHAR(64)  NOT NULL,
    model_name               VARCHAR(128) NOT NULL,
    description              TEXT,
    temperature              DOUBLE PRECISION,
    max_tokens               INT,
    max_input_tokens         INT,
    request_timeout_seconds  INT,
    top_p                    DOUBLE PRECISION,
    enable_search            BOOLEAN      NOT NULL DEFAULT FALSE,
    search_strategy          VARCHAR(32),
    builtin                  BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    model_type               VARCHAR(32)  NOT NULL DEFAULT 'chat',
    modalities               TEXT,
    create_time              TIMESTAMP    NOT NULL,
    update_time              TIMESTAMP    NOT NULL,
    deleted                  INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_ws_model_config UNIQUE (workspace_id, provider, model_name)
);
CREATE INDEX IF NOT EXISTS idx_ws_model_config_workspace
    ON mate_workspace_model_config(workspace_id);
CREATE INDEX IF NOT EXISTS idx_ws_model_config_default
    ON mate_workspace_model_config(workspace_id, is_default, enabled);
