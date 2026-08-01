-- Cloud identity is composite: the same user id may exist in different tenants.
UPDATE mc_workspace_user
SET yliyun_tenant_id = '1'
WHERE yliyun_tenant_id IS NULL OR TRIM(yliyun_tenant_id) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_ws_user_yliyun_tenant_user
    ON mc_workspace_user(yliyun_tenant_id, yliyun_user_id);
