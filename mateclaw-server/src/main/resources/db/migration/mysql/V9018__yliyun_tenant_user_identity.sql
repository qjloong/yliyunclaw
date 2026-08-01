-- Cloud identity is composite: the same user id may exist in different tenants.
UPDATE mc_workspace_user
SET yliyun_tenant_id = '1'
WHERE yliyun_tenant_id IS NULL OR TRIM(yliyun_tenant_id) = '';

ALTER TABLE mc_workspace_user
    ADD CONSTRAINT uk_mc_ws_user_yliyun_tenant_user
    UNIQUE (yliyun_tenant_id, yliyun_user_id);
