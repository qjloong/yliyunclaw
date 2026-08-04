# Trusted Context Consumer

Trusted Context 由云盘服务端创建，MateClaw 只消费和验证。

## 验证顺序

1. 验证服务 Token 与 HMAC 签名。
2. 校验时间窗、Nonce 防重放和 `Idempotency-Key`。
3. 校验 Header 与签名 Context 中的 tenant、user、workspace、app、biz、run、trace 一致。
4. 校验一粒云用户映射、Workspace 归属和应用 entitlement/configVersion。
5. 将通过验证的上下文写入服务端执行上下文；模型输入只接收最小必要描述。

## 禁止事项

- 不信任前端直接提交的 tenantId、userId、workspaceId 或角色。
- 不从自然语言 Prompt 推导权限范围。
- 不把 Trusted Context 当作长期登录凭据。
- 不允许 Goal/AI Sites Tool 在缺少业务范围时降级为全租户访问。

正式字段和签名串见云盘契约主源；本文件不复制其 Schema。
