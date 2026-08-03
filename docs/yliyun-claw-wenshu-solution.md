# 一粒云 × WenShu × MateClaw 完整方案

> 2026-07-23 | 分支: dev-v2 | 2026-08-03 实施与需求口径补充

> **实现口径优先级**：本文主体保留总体设计和历史方案示例；若示例与以下口径冲突，以本节及 `handover-yliyun-integration.md` 的最新任务状态为准。
>
> - MateClaw 仅使用通用 `McpClientManager`，通过 Streamable HTTP `/mcp` 连接 `yliyun-mcp`，当前 manifest 为 14 个工具。
> - 浏览器免登录 ticket 只用于云盘到 MateClaw 的一次性 SSO，兑换后写入 HttpOnly Cookie；不再签发长期 JWT 到 URL 或 localStorage。
> - MateClaw 每次 Tool Call 生成短时 OBO 身份断言；MCP 验签后通过云盘内部接口换取当前用户 token。文中“把浏览器 ticket 直接传给 MCP”或固定 `X-Forward-User-Id` 的示例均视为历史草案。
> - 用户映射键为 `(tenantId, yliyunUserId)`，每个云盘租户映射到独立 Workspace，并幂等种子化 `builtin.yliyun_assistant`。
> - P0 的服务、协议、身份、登录、固定测试夹具、分层诊断、工具读写闭环、真实模型行为 E2E 和跨用户/跨租户 TC-6 已全部实施并复验。
> - P1 已实现确定性云盘文件上下文、附件连续性、云盘文件详情右侧精简 Agent 面板，以及 Agent 通过 `file.create/file.save` 直接回存云盘；云盘上下文附件仅在会话首次提问或切换文件/文件夹后再次注入，普通追问不重复展示；完整 `CloudResourceRef`、固定引用、保存确认 UI 和 Codex 风格输入框合并仍待后续实施。
> - 最新任务计数为 P0 28/28 完成、P1 20/36 完成（另有 2 项进行中、1 项待验收）、P2 0/7、V3 2/34、统一应用接入治理 IG 15/28 完成（另有 1 项进行中）；租户 1 已完成 AI 助手应用安装、capability、签名 launchContext、真实身份 SSO、ticket 防重放、MCP entitlement/configVersion、平台管理员 MCP 边界、独立部署 Profile、分层诊断、双密钥无中断轮换、动态地址拒绝/切换/恢复、完整发布矩阵和通用可拖动非模态窗口/单 Header/多窗口浏览器验收；租户 135 已完成未安装状态隔离，以及经授权临时安装后的独立启停、真实租户管理员 SSO、Workspace owner/模型配置权限、旧会话失效、MCP 专用 delegation 精确撤销、MateClaw 不可达时的持久化补偿/自动恢复、新 ticket 恢复和最终卸载回收；WenShu 撤销失败现具备同等的持久化分步骤重试、旧端点补偿、告警、恢复和卸载保护，平台/租户配置边界测试已通过，逐项状态与证据以 `handover-yliyun-integration.md` 第十二至十五章为准。
> - 版本状态审计结论：V1 是“核心业务链已完成、生产收口未完成”，不能标记为全部完成；V2 设计中的专用文本读写、全局搜索、空间/用户上下文、`wenshu.analyze` 和全客户端验收尚未落地，当前仍以 V1 fallback 为主。
> - V3 以 MateClaw 会话为主入口的资源动作协议、生成物回存、云盘原生预览/播放、租户审计问数和云盘知识库同步方案及任务编号，统一维护在 `handover-yliyun-integration.md` 第十三章。
> - OAuth2 应用、云盘应用中心、MateClaw MCP 管理和 MCP 服务环境配置保持分层：OAuth2 Client 不作为租户开关；应用中心是租户 entitlement 和动态地址事实源；全局 MCP 连接由平台管理员管理；每次 Tool Call 继续使用 OBO 代表真实云盘用户。统一治理任务维护在 `handover-yliyun-integration.md` 第十四章。

---

## 〇、统一应用接入与认证治理（2026-07-30 新增，2026-08-03 补充）

### 0.1 当前配置与认证边界

| 配置域 | 当前范围 | 结论 |
|---|---|---|
| 云盘 OAuth2 应用 `system_oauth2_client` | 平台全局，实体使用 `@TenantIgnore` | 只管理 OAuth2 客户端、scope、grant、回调和 Token 生命周期；不能通过全局 Client 状态实现指定租户启停 |
| 云盘应用中心 | 应用清单 + `cloud_app_instance` 租户实例 | 作为应用安装、租户启停、动态地址、角色/文件策略、健康状态和生命周期的唯一业务事实源 |
| MateClaw `mate_mcp_server` | 当前全局，无 Workspace/Tenant 字段 | 管理 transport、URL、headers、连接和工具发现；平台共享 MCP 配置只能由平台管理员修改 |
| 一粒云 MCP 环境配置 | 单个独立部署实例 | 管理云盘 API、内部连接 Token、OBO 公钥/issuer/audience、云盘换票 appKey、限流与日志 |

当前问数 `wenshu_integration` 已形成应用中心闭环：平台连接配置从系统租户读取，租户策略从当前租户读取；enable/disable 同步远端生命周期；capability 决定入口；一次性 ticket 兑换为短时 delegation 后访问窄化文件 API。

AI 助手已从固定入口升级为应用中心治理实现，租户 1 已启用；租户 135 的临时安装/启停/回收矩阵、动态改址、双密钥轮换和 AI 助手发布矩阵已经完成，当前处于跨应用治理收口阶段：

- `mateclaw_ai_assistant` 已作为独立应用扩展注册，系统租户维护 MateClaw 地址/Origin/票据契约，业务租户维护启用、角色、导航、打开方式和文件能力策略。
- 云盘菜单、文件操作入口及嵌入地址已改为 `/admin-api/yliyun/ai/capability` 驱动，不再把 `VITE_AI_BASE_URL` 作为运行时事实源；问数继续走独立的 WenShu capability。
- AI ticket 出票前校验 installed/enabled/healthy/allowed，并签名 appKey、configVersion、state/nonce、parentOrigin 和 launchContext；MateClaw 对应验票。
- MateClaw OBO 已透传 appKey/configVersion；MCP 每次 Tool Call 重查租户 capability，并按通用工具 manifest 风险落实 read/write/share/delete 上限。
- MateClaw 应用 Cookie 已通过签名回调与 configVersion 主动失效，撤销回调同时固定 `mateclaw_ai_assistant` appKey；MCP OBO 使用独立的 10 分钟 OAuth Client Token，生命周期可按 tenant+client 精确撤销，不影响云盘用户登录。AI 助手与 WenShu 的撤销失败都会写入各自租户应用实例，按失败子步骤指数退避重试，达到阈值写结构化告警并通知租户联系人，恢复后自动清理和通知；待办保存原远端地址，健康刷新与卸载不会丢失补偿状态。AI 租户 135 已通过 MateClaw 停机/恢复实测，WenShu 失败、恢复、改址和告警自动化回归通过。ticket 与撤销签名现使用带 key id 的 current/previous 密钥环，按“先 MateClaw、后云盘”完成旧 key 和新 key 两阶段真实 SSO 验证，secret 不进入应用中心、前端、URL 或日志。平台动态地址写入会先校验候选 API 健康，失败事务回滚；成功后广播所有已安装租户并定向撤销旧端点，前端显式打开时强制刷新 capability，真实切换和恢复已通过。AI 助手完整发布矩阵及跨应用撤销治理已经归档；IG-P0 当前仅剩 AI 远端 Workspace/Agent 停用语义与回收策略，因此仍不能宣称 IG-P0 全部完成。

### 0.2 当前四段认证链

```text
云盘登录用户
  └─ YLIYUN_TICKET_SECRET：一次性 SSO ticket
       └─ MateClaw 映射 (tenantId, userId) → Workspace/owner/admin/member
            └─ MCP_INTERNAL_SERVICE_TOKEN：MateClaw 建立 MCP 服务连接
                 └─ OBO RS256：每次 Tool Call 注入真实云盘用户和租户
                      └─ YLIYUN_APP_KEY：MCP 换取/复用该用户 OAuth2 Token
                           └─ 云盘 API 以该用户 ACL 执行最终授权
```

这些凭据用途不同，禁止复用：

1. `YLIYUN_TICKET_SECRET`：云盘与 MateClaw 的浏览器 SSO。
2. `MCP_INTERNAL_SERVICE_TOKEN`：MateClaw 与 MCP 的连接级服务认证。
3. OBO 私钥/公钥：MateClaw 到 MCP 的逐次调用身份断言。
4. `YLIYUN_APP_KEY`：MCP 到云盘的内部用户换票授权。
5. WenShu `client_secret`：问数服务生命周期、票据兑换和回调认证。

### 0.3 统一目标

所有外部 AI/数据应用统一遵循：

1. 平台管理员配置服务/API/嵌入地址、Origin、认证协议、密钥和依赖。
2. 租户管理员安装、启停并配置本租户角色和数据能力策略。
3. 云盘前端通过统一 capability 动态展示菜单和打开方式，不持有服务密钥或构建期外部地址。
4. 用户启动应用时取得带 appKey、tenant/user、aud、jti、configVersion 和 launchContext 的一次性 ticket。
5. 远端应用通过 OBO 或 delegation 代表当前用户访问云盘，云盘后端继续执行租户、应用 entitlement、用户角色和资源 ACL 四层校验。
6. 应用停用同时关闭 capability、拒绝新出票、撤销旧会话/委托并拒绝 MCP 换票和 Tool Call，不能只隐藏菜单。

AI 助手注册为 `mateclaw_ai_assistant` 应用扩展；平台配置 MateClaw API/嵌入地址、allowed origin、ticket/OIDC 和 MCP audience，租户配置 enabled、allowed roles、文件读写/分享/删除范围、打开方式和导航策略。WenShu 保持现有业务逻辑，通过兼容适配迁移到同一连接器契约。

### 0.4 实施优先级

| 优先级 | 工作包 | 目标 | 发布闸门 |
|---|---|---|---|
| IG-P0-A | AI 助手应用中心化 | 租户实例、平台/租户配置分层、capability、动态地址、enable/disable/health | 地址变化无需重构前端；未启用租户没有入口 |
| IG-P0-B | SSO 与三层安全强制 | 通用 launch ticket；出票、验票、MCP 换票/Tool Call 同时校验 entitlement；停用撤销和双密钥轮换 | 绕过菜单或持有旧 Cookie 也不能继续调用 |
| IG-P0-C | MCP 平台治理 | 全局 MCP 配置只允许平台管理员；独立部署 Profile；分层诊断；双租户 E2E | transport、OBO、云盘换票、租户开关和 ACL 全部通过 |
| IG-P1 | 通用应用连接框架 | 版本化 SPI、WenShu 兼容适配、OAuth2 Profile 绑定、配置事件、依赖模型和管理页 | 现有用户、Workspace、问数入口和配置可原地迁移、回滚 |
| IG-P2 | MCP 业务扩展 | ToolProvider 模块、受控只读 OpenAPI 映射、统一风险策略、全文检索和审计报告工具域 | 新工具发现不等于授权；禁止任意 URL/Header/SQL |

当前 MCP 的 14 个工具为代码显式注册。新增复杂认证、数据转换或副作用工具仍需构建和部署 MCP；MateClaw通常只需刷新 MCP 连接和工具清单，不需要随每个新 Tool 重新打包。完成 IG-P2 后，仅批准的简单只读 API 可通过声明式映射免 MCP 代码重打包。

详细的 28 个任务、依赖、责任仓库、验收标准和执行批次以 `handover-yliyun-integration.md` 第十四章为准。实施顺序固定为 `IG-P0-A01 → IG-P0-A → IG-P0-B → IG-P0-C → IG-P1 → IG-P2`；P1 的签名资源引用可在 IG-P0 接口冻结后并行，全文检索和审计工具分别复用 V3-A05、V3-E，不建设第二套云盘 API。

### 0.5 V3 媒体播放、外链与本地应用边界

V3 的“打开/播放”不是让 LLM 直接输出一个 URL，也不是统一交给 `browser_use`。统一采用 `CloudResourceAction + 客户端动作分发器`：

```text
Agent / MCP
  └─ 返回资源引用、PLAY/OPEN_PREVIEW/OPEN_LOCAL 意图、能力和短时 actionRef
       ├─ 云盘内嵌：postMessage → 云盘宿主校验 → 复用原生预览/播放器
       ├─ 独立 Web：动作卡 → 用户点击 → window.open 云盘应用短时地址
       └─ 云盘桌面端：宿主动作 → Tauri/本地编辑协议 → 系统播放器或 Office
```

当前错误地址已经定位：MCP 创建分享后丢弃云盘后端返回的 `shareUrl`，使用 `YLIYUN_API_BASE_URL` 拼接 `/share/{shareCode}`；服务端调用又没有浏览器 Origin，最终容易得到后端 API Host。修复后由云盘后端输出相对 `sharePath` 或基于受信云盘 Web Base 的 `publicUrl`，MCP 必须原样透传。当前用户的私有预览/播放使用短时启动票据，不能默认创建公开分享。

此前 `browser_use` 不会出现在云盘助手工具集中，是因为 `AgentGraphBuilder` 对 `builtin.yliyun_assistant` 强制只保留 `yliyun-mcp` Callback。该模板级硬限制已于 2026-07-30 删除；云盘助手现在与普通 Agent 一样，使用 Agent/Skill 工具绑定、渐进披露和运行时 Guard，管理员可以通过通用工具配置决定可用能力，同时仍受现有权限与安全策略约束。`browser_use` 即使按通用配置启用，控制的也是 MateClaw 后端所在机器的 Playwright/CDP 浏览器，不等于打开访问网页用户的本机浏览器；客户端打开/播放仍应通过可审计的 `CloudResourceAction`。

MP4 播放不要求桌面客户端：云盘 Web 内嵌可直接调宿主播放器，独立 MateClaw Web 可由用户点击动作卡在浏览器打开。只有“自动调用本机已安装 Office，并监听修改回传云盘”的闭环需要云盘桌面端、原生帮助程序或已安装的 `cloud-drive://` 协议处理器；纯 Web 应回退到 OnlyOffice、在线预览或下载。浏览器的弹窗和媒体自动播放策略要求保留用户手势，不能承诺无点击自动播放。

完整定位证据、运行形态矩阵及 `V3-D01`—`V3-D09` 任务以 `handover-yliyun-integration.md` 第 13.2.1、13.3 节为准。

---

## 一、总体架构

```
                     ┌──────────────────────────────────┐
                     │     外部 AI 客户端 (MCP 协议)       │
                     │  Claude Desktop │ Codex │ Cursor  │
                     │  Windsurf │ ChatGPT │ Kimi ...    │
                     └────────────┬─────────────────────┘
                                  │ MCP Streamable HTTP
                                  │ HTTPS + API Key
                                  ▼
┌─────────────────────────────────────────────────────────┐
│                  Nginx (HTTPS 反向代理)                   │
│  /mcp → yliyun-mcp  │  /api/ai → mateclaw  │  /wenshu   │
└──────────────────────┬──────────────────────────────────┘
                       │
     ┌─────────────────┼─────────────────┐
     │                 │                 │
     ▼                 ▼                 ▼
┌──────────┐   ┌──────────────┐   ┌──────────────┐
│Yliyun MCP│   │  MateClaw    │   │   WenShu     │
│Server    │   │  (通用AI引擎) │   │  (问数引擎)   │
│(TypeScript│  │  Agent+Wiki  │   │  NL2SQL      │
│ FastMCP) │   │  会话/审批/记忆│   │  DuckDB分析  │
│          │   │  多模型容错   │   │  报告/图表    │
│file.*    │◄──│  MCP Client  │◄──│              │
│space.*   │   │  (已有)      │   │              │
│wenshu.*  │   └──────┬───────┘   └──────┬───────┘
└────┬─────┘          │                  │
     │                │                  │
     │  内部 HTTP      │                  │
     ▼                │                  │
┌──────────┐          │                  │
│ 一粒云云盘 │◄─────────┘                  │
│ (Java)   │                             │
│ 文件/权限 │                             │
└──────────┘                             │
     │                                    │
     └────────────────────────────────────┘
                      │
          ┌───────────▼───────────┐
          │  Docker Compose 统一部署 │
          │  MySQL + MinIO + Redis │
          └───────────────────────┘
```

**核心分工**：MateClaw = 通用 AI 助手（文件问答/写作/知识检索/会话管理）| WenShu = 高级问数引擎（表格深度分析）| MCP Server = 云盘能力标准化暴露

---

## 二、用户与组织架构对接

### 原则：云盘为主，AI 跟随

用户体系不需要在 AI 侧重建。通过一次性 code 兑票机制自动映射：

```
用户在云盘登录 → 打开 AI 浮窗
  → 云盘生成一次性 ticket（含 userId/tenantId/tenantName/tenantAdmin 并签名）
  → MateClaw 兑票验签、防重放 → 查找或创建映射用户及租户 Workspace
  → 设置 HttpOnly、SameSite Cookie → 后续请求不在 URL/localStorage 暴露长期 JWT
```

### MateClaw 用户表扩展

```sql
ALTER TABLE mc_workspace_user ADD COLUMN yliyun_user_id VARCHAR(64);   -- 云盘用户ID
ALTER TABLE mc_workspace_user ADD COLUMN yliyun_tenant_id VARCHAR(64); -- 租户ID
```

### 组织同步

| 方式 | 说明 |
|---|---|
| 事件驱动（推荐） | 云盘 Webhook `user.created/deleted` → MateClaw 同步 |
| 实时查询（兜底） | 权限校验时调用云盘 API 实时确认 |
| 当前实现 | 每次 SSO 按签名声明对账用户、租户 Workspace 和成员角色；组织树仍留待后续事件同步 |

### 租户角色与模型供应商隔离（2026-07-29 已实施）

云盘是租户角色的事实源，前端不得传入或覆盖管理员身份。云盘后端通过
`PermissionService` 判断 `super_admin` 或 `tenant_admin`，把布尔值 `tenantAdmin` 放入签名 ticket；
MateClaw 每次 SSO 同步 `mc_workspace_user` 和 `mate_workspace_member`：

| 云盘身份 | MateClaw Workspace 角色 | 能力 |
|---|---|---|
| 首位有效租户管理员 | owner | 管理成员、配置本租户供应商与模型 |
| 后续租户管理员 | admin | 配置本租户供应商与模型 |
| 普通租户成员 | member | 使用并只读查看本租户已启用模型 |

管理员降级时，若其为 owner，系统先把 owner 转交给其他有效管理员，再完成降级；
禁止通过设置全局 `mate_user.role='admin'` 实现租户管理，否则会越过 Workspace 边界。

供应商采用“平台目录 + 工作空间配置”双层模型：

| 数据 | 存储与隔离方式 |
|---|---|
| 内置 Provider 名称、协议、能力元数据 | 复用平台 `mate_model_provider` 目录，不向租户泄露平台密钥 |
| 租户 API Key、OAuth、Base URL、启用状态、回退优先级 | `mate_workspace_model_provider`，唯一键 `(workspace_id, provider_id)`，敏感字段 AES-GCM 加密 |
| 租户模型清单、默认模型、启用状态 | `mate_workspace_model_config`，唯一键 `(workspace_id, provider, model_name)` |
| 旧平台配置 | 默认 Workspace 继续读取原全局表，不做破坏性复制或密钥重写 |

模型管理接口必须显式提供 `X-Workspace-Id` 并校验成员资格：admin/owner 可写，
member/viewer 只读可用模型。Agent 构建、工具调用、OAuth 回调和模型选择均携带同一
Workspace scope。租户配置不注册到进程级全局 Provider Pool/熔断器，从而避免一个
租户的失败影响其他租户。

Claude Code OAuth 凭据存放在 MateClaw 服务器本机用户目录，无法按 Workspace 加密隔离，
因此仅在平台默认 Workspace 可见；租户可使用工作空间化的 API Key、自定义 OpenAI
兼容供应商和 OpenAI OAuth。

### 云盘文件问答运行口径（2026-07-29 已验收）

云盘附件在会话入口携带 `yliyun://file/{id}` 引用。MateClaw 使用当前用户 OBO 身份通过
`yliyun-mcp` 的 `file.read` 确定性解析文件，把带来源标识的内容写入会话上下文；导航到
模型设置再返回、展开完整界面或在同一会话继续追问时，不要求用户重新选择或上传文件。
云盘宿主首次打开文件时，把资源作为下一轮待分析附件；发送后清除附件卡但保留活动上下文和会话记忆。普通追问不重复附加同一资源；宿主切换文件/文件夹后才为下一轮重新加入附件，不重载 iframe，也不创建新会话。

`builtin.yliyun_assistant` 只装配 `yliyun-mcp` 的 14 个工具，避免全局工具清单占满本地
模型上下文。当前环境的 `ollama/qwen3:latest` 实际上下文为 4096，收敛工具后仍返回空
响应，因此依照运行回退规则切换为租户已配置的 `deepseek/deepseek-chat`。文件 `17485`
（XLSX，3 个工作表）已完成首轮总结和不重传附件的同会话追问，答案持久化成功。

工作空间 Agent 不使用同名进程级全局 Provider Pool/熔断状态过滤租户 Provider，确保
其他工作空间或平台探测失败不会错误禁用当前租户的供应商。

---

## 三、云盘集成方式 — Yliyun MCP Server 详细设计

### 3.0 为什么选 MCP Server

MCP 是 AI 行业事实标准协议，所有主流 AI 平台（Claude Desktop、Codex、Cursor、Windsurf、ChatGPT、Kimi 等）均已原生支持。将云盘能力封装为 MCP Server，一次开发、所有 AI 客户端通用，是 ROI 最高的方案。

| 维度 | MCP Server | 硬编码 Plugin |
|---|---|---|
| 跨平台 | ✅ 任何 MCP 客户端可消费 | ❌ 仅 Java 生态 |
| 安全 | ✅ 权限在云盘侧校验 | ⚠️ 需要二次开发 |
| 维护 | ✅ 标准协议，扩展不改 AI 侧 | ❌ 每次改 API 都要改 Plugin |
| 生态兼容 | ✅ Claude/Codex/Cursor/ChatGPT 等 | ❌ 仅 MateClaw |

---

### 3.1 技术栈选型

#### 候选方案对比

MCP Server 作为独立服务部署，通过 HTTP 调用云盘内部 API。语言选型不受云盘（Java）限制，核心考量是 **MCP 生态成熟度** 和 **跨客户端兼容性**。

| 维度 | TypeScript (FastMCP) | Python (FastAPI-MCP) | Java (Quarkus MCP) | Go (mcp-go) |
|---|---|---|---|---|
| **MCP 生态成熟度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐½ | ⭐⭐⭐ |
| **SDK 官方支持** | ✅ Anthropic 官方 SDK | ✅ 官方 Python SDK | ✅ Spring AI / Quarkus | ⚠️ 社区维护 |
| **Streamable HTTP** | ✅ 完整支持 | ✅ 支持 | ✅ 支持 | ⚠️ 部分支持 |
| **跨客户端兼容性** | ✅ 最广泛验证 | ✅ 良好 | ✅ 良好 | ⚠️ 验证较少 |
| **开发效率** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **运行性能** | 良好（I/O 优化） | 中等（GIL 限制） | 优秀（原生编译） | 优秀（原生二进制） |
| **团队技术栈契合度** | 中等 | 中等 | ✅ 高（现有 Java 团队） | 低 |
| **社区示例/文档** | 最丰富 | 丰富 | 中等 | 较少 |
| **OAuth 2.1 支持** | ✅ FastMCP 内建 | ✅ FastAPI 中间件 | ✅ Spring Security | ⚠️ 手动集成 |
| **热重载开发体验** | ✅ tsx --watch | ✅ uvicorn --reload | ✅ Quarkus dev mode | ✅ air |
| **Docker 镜像大小** | ~150MB | ~200MB | ~100MB（原生） | ~15MB |

#### 推荐方案：TypeScript + FastMCP（首选），Java/Quarkus（备选）

**首选 TypeScript 的理由**：

1. **官方 SDK 一等公民**：MCP 协议由 Anthropic 主导，TypeScript SDK 是官方参考实现，新特性最先落地
2. **跨客户端验证最充分**：Claude Desktop、Codex、Cursor、Windsurf 等主流客户端均以 TypeScript MCP Server 为主要测试目标
3. **FastMCP 框架成熟**：提供 Express 风格 API、Session 管理、SSE/Streamable HTTP 双传输、OAuth 认证钩子、CLI 工具链，开箱即用
4. **Zod 验证深度集成**：工具参数定义 → JSON Schema → 运行时验证，一处定义三处生效
5. **NPM 生态丰富**：文件处理、MIME 检测、流式传输等成熟库可直接使用
6. **降低维护成本**：社区活跃，协议更新跟进快；TypeScript 开发者更容易招聘

**备选 Java 的场景**：如果团队坚决要求统一技术栈、或需要深度复用云盘 Java SDK 内部代码，可选择 Quarkus MCP Server SDK。但需注意社区成熟度和跨客户端验证投入更大。

```typescript
// FastMCP 开发体验示例
import { FastMCP } from 'fastmcp';

const server = new FastMCP({
  name: 'yliyun-mcp',
  version: '1.0.0',
  authenticate: async (req) => { /* ticket/JWT 验证 */ }
});

server.addTool({
  name: 'file.search',
  description: '在云盘中搜索文件。支持按文件名、内容、标签、文件类型过滤。返回匹配文件列表及权限信息。',
  parameters: z.object({
    query: z.string().min(1).describe('搜索关键词'),
    spaceId: z.string().optional().describe('限定搜索的空间ID，不传则搜索用户有权限的所有空间'),
    fileType: z.enum(['document','spreadsheet','image','pdf','video','all']).default('all'),
    maxResults: z.number().int().min(1).max(100).default(20),
  }),
  execute: async (args, { session }) => {
    return await cloudDriveAPI.search(args, session.userId);
  }
});
```

---

### 3.2 架构设计

#### 分层架构

```
┌─────────────────────────────────────────────────────┐
│                   AI 客户端层                         │
│  Claude Desktop │ Codex │ Cursor │ MateClaw │ ...    │
│         │           │       │         │              │
│         └───────────┴───────┴─────────┘              │
│                     │ MCP 协议                        │
│         Streamable HTTP / SSE / stdio                │
└─────────────────────┬───────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────┐
│                Yliyun MCP Server                     │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │          Transport Layer                     │    │
│  │  Streamable HTTP (主) │ SSE │ stdio (本地)    │    │
│  │  POST /mcp  │  GET /mcp (SSE)  │  Auth 中间件  │    │
│  └────────────────────┬────────────────────────┘    │
│                       │                              │
│  ┌────────────────────▼────────────────────────┐    │
│  │          Protocol Layer                      │    │
│  │  JSON-RPC 2.0 编解码                         │    │
│  │  tools/list │ tools/call │ resources/read     │    │
│  │  错误标准化 │ 分页处理  │ 幂等控制             │    │
│  └────────────────────┬────────────────────────┘    │
│                       │                              │
│  ┌────────────────────▼────────────────────────┐    │
│  │          Business Logic Layer                │    │
│  │  FileService │ SpaceService │ UserService     │    │
│  │  SearchEngine │ TagService │ ShareService    │    │
│  │  WenshuBridge │ AuditLogger                  │    │
│  └────────────────────┬────────────────────────┘    │
│                       │                              │
│  ┌────────────────────▼────────────────────────┐    │
│  │          Cloud Drive API Client              │    │
│  │  HTTP Client (连接池) │ 重试/熔断 │ 缓存       │    │
│  │  Ticket 兑票 │ Permission Check               │    │
│  └────────────────────┬────────────────────────┘    │
└───────────────────────┼─────────────────────────────┘
                        │ HTTP (内网)
┌───────────────────────▼─────────────────────────────┐
│              一粒云云盘 (Java)                        │
│  /api/v1/files  │  /api/v1/spaces  │  /api/v1/users  │
│  文件CRUD       │  空间管理         │  用户/权限       │
└─────────────────────────────────────────────────────┘
```

#### 关键设计决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| **传输协议** | Streamable HTTP（主力）+ SSE（兼容旧客户端） | 代理友好、无持久连接要求、跨防火墙兼容性最好 |
| **认证方式** | Ticket 兑票为主 + API Key 为辅 + OAuth 2.1（远期） | 一期快速打通云盘用户体系；远期为外部 AI 客户端提供 OAuth |
| **工具数量** | ≤20 个工具，按类别分组 | 超过 20 个工具会降低模型选择准确率；通过参数扩展而非增加工具 |
| **权限模型** | 每次调用实时验权（调用云盘 API 确认） | 不缓存权限，避免权限变更后的越权风险 |
| **文件传输** | 元数据返回 URL + 可选内容内联 | 大文件不经过 MCP Server，直接返回云盘下载 URL |
| **错误处理** | 结构化错误 + suggestion 字段 | 帮助 AI 模型自我纠正，减少重试循环 |
| **幂等性** | 所有写操作带 idempotency_key | 防止 AI 模型重试导致重复创建/修改 |
| **日志审计** | 每次 Tool 调用记录：工具名、参数（脱敏）、用户、时间、结果 | 满足企业审计要求 |

#### 与 MateClaw 的关系

```
外部 AI 客户端 (Claude Desktop / Codex / Cursor)
    │
    │ MCP Streamable HTTP
    ▼
Yliyun MCP Server ──HTTP──▶ 云盘 API
    │
    │ MCP Streamable HTTP (同样协议)
    ▼
MateClaw MCP Client (已有)
    │
    ▼
MateClaw Agent → 将 MCP Tools 注入 Agent 工具列表
```

MateClaw 已有的 `McpClientManager` 支持 Streamable HTTP 传输，配置即可接入，零代码改动。

---

### 3.3 MCP Tools 详细设计

#### 设计原则

1. **一个 Tool 做一件事**：`file.read` 只读内容，`file.search` 只搜索，避免 God Tool
2. **参数即意图**：参数名和描述让 AI 无需额外上下文即可理解用途
3. **返回即答案**：返回结构包含足够信息，AI 无需二次查询
4. **错误可行动**：错误消息告诉 AI "下一步该调哪个 Tool"

#### 3.3.1 文件搜索与读取

```yaml
# file.search — 云盘文件搜索
name: file.search
description: >
  在云盘中全文搜索文件。支持按文件名、文件内容（已索引的文档）、
  标签、文件类型进行过滤。返回匹配文件列表，包含文件名、路径、
  大小、修改时间、标签、权限（只读/可编辑）等信息。
  
  使用场景：用户问"找一下上周的合同文件"、"搜一下包含'预算'的Excel"
  
  提示：如果搜索结果太多，建议用 fileType 和 spaceId 缩小范围。
parameters:
  query:
    type: string
    description: 搜索关键词，支持文件名和内容全文搜索
  spaceId:
    type: string
    description: 限定搜索的空间ID。不传则搜索用户有权限的所有空间和文件
  fileType:
    type: string
    enum: [document, spreadsheet, image, pdf, video, audio, archive, all]
    default: all
    description: 按文件类型过滤
  tags:
    type: array
    items: {type: string}
    description: 按标签过滤（AND 逻辑）
  dateFrom:
    type: string
    description: 修改时间起始 (ISO 8601)
  dateTo:
    type: string
    description: 修改时间截止 (ISO 8601)
  maxResults:
    type: integer
    default: 20
    minimum: 1
    maximum: 100
returns:
  files: array
    - id: string
      name: string
      path: string
      size: integer
      mimeType: string
      modifiedAt: string
      tags: [string]
      permission: {read: boolean, write: boolean, share: boolean}
  total: integer
  hasMore: boolean
  suggestion: string  # 如 "结果较多，建议添加 fileType 过滤或缩小日期范围"

# file.read — 读取文件内容
name: file.read
description: >
  读取指定文件的内容。对于文本类文件（文档、代码、 Markdown 等）返回文本内容；
  对于 Office 文件（Word/Excel/PPT）返回提取的文本和结构化数据摘要；
  对于图片返回 OCR 文本和图片元数据；对于 PDF 返回提取的文本和页数。
  
  不支持直接返回二进制内容。大文件默认截断到指定行数/字符数。
  
  使用场景：用户问"帮我看看这份合同的内容"、"总结一下这个 PDF"
parameters:
  fileId:
    type: string
    description: 文件ID（可从 file.search 或 file.list 获取）
  maxChars:
    type: integer
    default: 50000
    description: 最大返回字符数，超出截断并标记
  format:
    type: string
    enum: [text, markdown, raw]
    default: markdown
    description: 返回格式。markdown 保留排版结构，text 纯文本
returns:
  content: string
  fileName: string
  mimeType: string
  charCount: integer
  truncated: boolean
  metadata:
    author: string
    createdAt: string
    pageCount: integer  # 仅 Office/PDF
    sheetNames: [string]  # 仅 Excel

# file.list — 浏览目录
name: file.list
description: >
  列出指定目录下的文件和子目录。类似文件系统的 ls 命令。
  
  使用场景：用户问"看看项目文件夹里有什么文件"、"列出最近的文件"
parameters:
  spaceId:
    type: string
    description: 空间ID
  parentPath:
    type: string
    default: "/"
    description: 父目录路径，默认根目录
  sortBy:
    type: string
    enum: [name, modifiedAt, size, type]
    default: modifiedAt
  order:
    type: string
    enum: [asc, desc]
    default: desc
  maxResults:
    type: integer
    default: 50
returns:
  items: array
    - id: string
      name: string
      type: [file, directory]
      size: integer
      mimeType: string
      modifiedAt: string
  parentPath: string
  total: integer
```

#### 3.3.2 文件写入与管理

```yaml
# file.create — 创建文件或目录
name: file.create
description: >
  在云盘中创建新文件或目录。创建文件时需要提供内容；
  创建目录时只需提供路径。
  
  使用场景：用户说"帮我把这份会议纪要保存到项目文件夹"、
  "创建一个名为'2026Q3'的目录"
parameters:
  spaceId:
    type: string
    description: 目标空间ID
  parentPath:
    type: string
    default: "/"
  name:
    type: string
    description: 文件名（含扩展名）或目录名
  type:
    type: string
    enum: [file, directory]
    default: file
  content:
    type: string
    description: 文件内容（type=file 时必填）
  mimeType:
    type: string
    description: 文件 MIME 类型，如 text/markdown, application/pdf
  idempotencyKey:
    type: string
    description: 幂等键，避免重复创建。同一 key 的重复请求返回已创建的文件
returns:
  fileId: string
  name: string
  path: string
  url: string  # 云盘 Web 访问链接

# file.save — 保存/更新文件内容
name: file.save
description: >
  更新已有文件的内容。会创建新版本（如果云盘开启了版本管理）。
  
  使用场景：AI 改写/润色完文档后保存回云盘、更新数据文件
parameters:
  fileId:
    type: string
    description: 文件ID
  content:
    type: string
    description: 新的文件内容
  createVersion:
    type: boolean
    default: true
    description: 是否创建新版本
  versionNote:
    type: string
    description: 版本说明（如 "AI 润色修改"）
  idempotencyKey:
    type: string
returns:
  fileId: string
  versionId: string
  url: string

# file.move — 移动/重命名文件
name: file.move
description: >
  移动文件到新位置或重命名文件。
  
  使用场景：用户说"把这个文件移到归档目录"、"把报告重命名为 v2.0"
parameters:
  fileId:
    type: string
  targetSpaceId:
    type: string
    description: 目标空间ID
  targetPath:
    type: string
    description: 目标路径（含新文件名实现重命名）
returns:
  fileId: string
  oldPath: string
  newPath: string

# file.delete — 删除文件（移到回收站）
name: file.delete
description: >
  将文件移到回收站。云盘默认保留回收站30天，期间可恢复。
  永久删除需要用户在云盘 Web 端确认。
  
  使用场景：用户说"删掉这个临时文件"
parameters:
  fileId:
    type: string
  permanent:
    type: boolean
    default: false
    description: 是否永久删除（需要额外权限确认）
returns:
  fileId: string
  deleted: boolean
  recoveryAvailableUntil: string  # 回收站恢复截止时间
```

#### 3.3.3 文件协作与元数据

```yaml
# file.tag — 管理文件标签
name: file.tag
description: >
  为文件添加或移除标签。标签用于分类和搜索。
  
  使用场景：用户说"给这个文件打上'合同'和'重要'的标签"
parameters:
  fileId:
    type: string
  action:
    type: string
    enum: [add, remove, set]
    description: add=追加标签, remove=移除指定标签, set=覆盖设置
  tags:
    type: array
    items: {type: string}

# file.share_link — 创建分享链接
name: file.share_link
description: >
  为文件或目录创建外部分享链接。可设置密码、有效期和权限。
  
  使用场景：用户说"把这个文件分享给外部合作伙伴，设置7天有效期"
parameters:
  fileId:
    type: string
  password:
    type: string
    description: 访问密码（可选）
  expireDays:
    type: integer
    default: 7
    minimum: 1
    maximum: 365
  permission:
    type: string
    enum: [view, download, edit]
    default: view
returns:
  shareUrl: string
  password: string
  expiresAt: string

# file.versions — 查看文件版本历史
name: file.versions
description: >
  获取文件的版本历史列表，可读取指定版本的内容。
  
  使用场景：用户问"这个文件之前的内容是什么"、"对比一下 v1 和 v3"
parameters:
  fileId:
    type: string
  versionId:
    type: string
    description: 指定版本ID则返回该版本内容，不传则返回版本列表
returns:
  versions: array  # versionId 为空时
    - versionId: string
      versionNumber: integer
      createdAt: string
      createdBy: string
      note: string
      size: integer
  content: string  # versionId 指定时
```

#### 3.3.4 上下文与集成工具

```yaml
# space.context — 获取空间上下文信息
name: space.context
description: >
  获取云盘空间的上下文信息，包括空间名称、成员、文件统计、
  最近活动等。Agent 可借此了解用户当前的工作场景。
  
  使用场景：Agent 初次对话时了解用户工作空间、切换空间时获取上下文
parameters:
  spaceId:
    type: string
    description: 空间ID，不传则返回用户最近活跃的空间
returns:
  spaceId: string
  name: string
  description: string
  memberCount: integer
  fileCount: integer
  totalSize: integer
  recentFiles: array  # 最近修改的5个文件
  isOwner: boolean
  isAdmin: boolean

# user.profile — 获取当前用户信息
name: user.profile
description: >
  获取当前登录用户的基本信息和云盘使用统计。
  
  使用场景：Agent 需要知道当前用户身份以个性化回答
returns:
  userId: string
  displayName: string
  email: string
  tenantId: string
  tenantName: string
  storageUsed: integer
  storageLimit: integer
  preferences:
    language: string
    timezone: string

# wenshu.analyze — 委托 WenShu 进行深度表格分析
name: wenshu.analyze
description: >
  将 Excel/CSV 文件发送给 WenShu 引擎进行深度数据分析。
  WenShu 支持 NL2SQL 自然语言查询、图表生成、数据透视等。
  
  使用场景：用户问"分析这个销售 Excel 的趋势"、
  "对比 Q1 和 Q2 的数据，做柱状图"
  
  注意：此工具适合复杂表格分析。简单的文档问答请使用 file.read。
parameters:
  fileId:
    type: string
    description: 要分析的 Excel/CSV 文件ID
  question:
    type: string
    description: 自然语言分析问题
  chartType:
    type: string
    enum: [auto, bar, line, pie, scatter, table]
    default: auto
    description: 期望的图表类型，auto 由 WenShu 自动选择
returns:
  answer: string      # 分析结论
  sql: string         # 生成的 SQL（可解释性）
  chartUrl: string    # 图表图片 URL
  tableData: object   # 表格数据（可选）
```

#### 3.3.5 工具分组总览

| 分组 | 工具 | 权限要求 |
|---|---|---|
| **文件发现** | `file.search`, `file.list` | 读权限 |
| **文件读取** | `file.read`, `file.versions` | 读权限 |
| **文件写入** | `file.create`, `file.save`, `file.move`, `file.delete` | 写权限 |
| **文件协作** | `file.tag`, `file.share_link` | 写权限 |
| **上下文** | `space.context`, `user.profile` | 登录即可 |
| **集成** | `wenshu.analyze` | 读权限 + WenShu 可用 |

> **总计 14 个工具**，控制在 20 个以内，确保模型工具选择准确率。

---

### 3.4 认证与安全设计

#### 认证流程

```
┌──────────┐     ┌──────────┐     ┌───────────┐     ┌──────────┐
│ AI 客户端 │     │ MCP Server│     │ 云盘       │     │ MateClaw │
└────┬─────┘     └────┬─────┘     └─────┬─────┘     └────┬─────┘
     │                 │                │                 │
     │ 1. MCP 连接     │                │                 │
     │ (带认证头)      │                │                 │
     │────────────────▶│                │                 │
     │                 │                │                 │
     │                 │ 2. 验票/验签    │                 │
     │                 │───────────────▶│                 │
     │                 │                │                 │
     │                 │ 3. 用户信息+权限 │                 │
     │                 │◀───────────────│                 │
     │                 │                │                 │
     │ 4. Tool 调用    │                │                 │
     │────────────────▶│                │                 │
     │                 │ 5. 实时验权     │                 │
     │                 │───────────────▶│                 │
     │                 │                │                 │
     │                 │ 6. 执行业务操作  │                 │
     │                 │───────────────▶│                 │
     │                 │                │                 │
     │ 7. Tool 结果    │                │                 │
     │◀────────────────│                │                 │
```

**一期认证方案（Ticket 兑票）**：

```
云盘用户登录 → 打开 AI → 云盘生成一次性 ticket
  → ticket 通过 MCP 请求头传递 (Authorization: Bearer <ticket>)
  → MCP Server 调用云盘 /api/v1/ticket/verify 验签
  → 获取 userId + tenantId + permissions
  → 后续 Tool 调用携带已验证的用户上下文
```

**认证头传递方式**：

```http
POST /mcp HTTP/1.1
Content-Type: application/json
Authorization: Bearer yli_ticket_xxxx
X-Tenant-Id: tenant_123
```

#### 多客户端认证策略

| 客户端类型 | 认证方式 | 配置方式 |
|---|---|---|
| **Claude Desktop** | API Key（手动配置到 `claude_desktop_config.json`） | `"headers": {"Authorization": "Bearer <key>"}` |
| **Codex** | API Key 或 OAuth（通过 `~/.codex/mcp.json`） | `"env": {"YLIYUN_API_KEY": "..."}` 或 OAuth flow |
| **Cursor** | API Key（`~/.cursor/mcp.json`） | `"headers": {"X-API-Key": "..."}` |
| **MateClaw** | 内部 Ticket（自动透传用户身份） | 通过 MateClaw MCP Server 配置界面 |
| **ChatGPT** | API Key（GPT Actions 配置） | 在 GPT 配置中设置 Authentication header |

#### 安全设计清单

| 安全措施 | 实现方式 |
|---|---|
| **每次调用验权** | 不缓存权限结果，每次 Tool 调用实时请求云盘验权 |
| **租户隔离** | 所有查询自动带 tenantId 过滤，防止跨租户数据泄露 |
| **参数校验** | Zod Schema 严格校验所有输入参数，拒绝非预期输入 |
| **注入防护** | 文件路径使用白名单验证（禁止 `../` 等路径遍历） |
| **文件类型白名单** | 读取/搜索只允许指定 MIME 类型，拒绝可执行文件等 |
| **速率限制** | Token Bucket，每用户 100 req/min，超限返回 429 + retry_after |
| **请求大小限制** | 请求体最大 10MB，文件内容写入最大 5MB/次 |
| **审计日志** | 结构化 JSON 日志，记录每次 Tool 调用的用户、参数（脱敏）、结果、耗时 |
| **敏感操作确认** | file.delete(permanent=true) 和 file.share_link 需要二次确认机制 |

---

### 3.5 错误处理设计

#### LLM 友好的结构化错误

传统 API 返回 `500 Internal Server Error` 会导致 AI 不断重试。MCP Server 的错误消息应引导 AI 采取正确的后续行动：

```typescript
// ✅ 好的错误消息
{
  "isError": true,
  "content": [{
    "type": "text",
    "text": JSON.stringify({
      "error": "FILE_NOT_FOUND",
      "message": "文件 'contract-2026.docx' 不存在或已被删除。",
      "suggestion": "使用 file.search 搜索文件名包含 'contract' 的文件，或使用 file.list 浏览目录。",
      "retryable": false
    })
  }]
}

// ✅ 权限错误
{
  "error": "PERMISSION_DENIED",
  "message": "你没有 fileId=xxx 的写入权限（当前为只读）。",
  "suggestion": "可以 file.read 查看内容，或联系文件所有者申请编辑权限。",
  "fileOwner": "zhangsan",
  "retryable": false
}

// ✅ 速率限制
{
  "error": "RATE_LIMITED",
  "message": "请求过于频繁，请稍后重试。",
  "retryAfter": 30,
  "suggestion": "等待 30 秒后重试，或减少并发请求数量。",
  "retryable": true
}
```

#### 错误码体系

| 错误码 | 说明 | 是否可重试 |
|---|---|---|
| `FILE_NOT_FOUND` | 文件不存在或已删除 | ❌ |
| `PERMISSION_DENIED` | 无权限 | ❌ |
| `INVALID_PARAMETER` | 参数校验失败 | ❌ |
| `QUOTA_EXCEEDED` | 存储空间不足 | ❌ |
| `RATE_LIMITED` | 触发频率限制 | ✅ (等待后) |
| `SERVER_ERROR` | 服务器内部错误 | ✅ (有限次) |
| `CLOUD_DRIVE_UNAVAILABLE` | 云盘服务不可用 | ✅ (有限次) |
| `WENSHU_UNAVAILABLE` | WenShu 服务不可用 | ✅ (有限次) |

---

### 3.6 项目结构

```
yliyun-mcp-server/
├── src/
│   ├── index.ts              # 服务入口，FastMCP 初始化
│   ├── server.ts             # Server 实例配置
│   ├── config/
│   │   └── index.ts          # 环境变量、云盘 API 地址等配置
│   ├── transport/
│   │   └── auth.ts           # 认证中间件（ticket 验签、API Key 验证）
│   ├── tools/
│   │   ├── index.ts          # 工具注册汇总
│   │   ├── file-search.ts    # file.search
│   │   ├── file-read.ts      # file.read
│   │   ├── file-list.ts      # file.list
│   │   ├── file-create.ts    # file.create
│   │   ├── file-save.ts      # file.save
│   │   ├── file-move.ts      # file.move
│   │   ├── file-delete.ts    # file.delete
│   │   ├── file-tag.ts       # file.tag
│   │   ├── file-share.ts     # file.share_link
│   │   ├── file-versions.ts  # file.versions
│   │   ├── space-context.ts  # space.context
│   │   ├── user-profile.ts   # user.profile
│   │   └── wenshu-analyze.ts # wenshu.analyze
│   ├── core/
│   │   ├── cloud-api.ts      # 云盘 API 客户端（HTTP 封装）
│   │   ├── wenshu-bridge.ts  # WenShu 服务桥接
│   │   ├── permission.ts     # 权限校验
│   │   └── audit.ts          # 审计日志
│   ├── schemas/
│   │   └── index.ts          # Zod Schema 定义（工具参数 + 返回值）
│   ├── errors/
│   │   └── index.ts          # 结构化错误定义
│   └── middleware/
│       ├── rate-limiter.ts   # 速率限制
│       ├── idempotency.ts    # 幂等性控制
│       └── request-logger.ts # 请求日志
├── tests/
│   ├── unit/
│   │   └── tools/            # 每个 Tool 的单元测试
│   ├── integration/
│   │   └── mcp-inspector/    # MCP Inspector 集成测试
│   └── cross-client/         # 多客户端兼容性测试脚本
├── docker/
│   ├── Dockerfile
│   └── docker-compose.mcp.yml
├── scripts/
│   ├── dev.sh                # 开发环境启动
│   ├── test-cross-client.sh  # 跨客户端测试
│   └── generate-openapi.ts   # 从云盘 OpenAPI 生成 Tool Schema
├── package.json
├── tsconfig.json
├── vitest.config.ts
└── README.md
```

---

### 3.7 与 MateClaw 的集成方式

MateClaw 侧**无需代码开发**，通过已有的 MCP Server 管理界面配置即可：

```yaml
# 在 MateClaw Admin → MCP Server 管理中添加
name: yliyun-mcp
transport: streamable_http
url: http://yliyun-mcp:18100/mcp
headers:
  X-Internal-Service: "mateclaw"
  X-Forward-User-Id: "${currentUserId}"   # MateClaw 自动注入当前用户
  X-Forward-Tenant-Id: "${currentTenantId}"
connectTimeoutSeconds: 10
readTimeoutSeconds: 30
disclosureTier: full   # 所有 Agent 可见
```

配置后，MateClaw Agent 的 Tool 列表中自动出现 `file.*`、`space.*` 等工具。Agent 在处理用户请求时，如果判断需要云盘操作（搜索文件、读取文档、保存结果），就会自动调用对应的 MCP Tool。

此外，可开发一个轻量 **YliyunPlugin**（可选）：
- 在 MateClaw webchat 启动时，自动调用 `space.context` + `user.profile` 注入上下文
- 提供快捷指令：`/云盘搜索` → 触发 file.search、`/保存到云盘` → 触发 file.create
- 在 MateClaw UI 展示云盘文件链接（可点击跳转云盘 Web 端）

---

## 四、WenShu 角色定位

### 当前分析结论

WenShu 和 Spec 描述的"AI 文件助手"是两个不同产品：

| | WenShu | AI 文件助手 (Spec) |
|---|---|---|
| 核心对象 | Excel/CSV 结构化数据 | 企业文件(文档/PDF/图片) |
| 核心能力 | NL2SQL → 深度表格分析 | RAG 问答 / 写作 / 出图 |
| 用户心智 | "分析这个数据集" | "总结这份文档" |

**WenShu 不应做**：RAG 问答、文档写作、图片生成、文件搜索（这些 MateClaw 已覆盖）。

**WenShu 应保留**：postMessage 协议 + 文件导入/回存 API + 问数分析核心能力。定位从"嵌入在云盘中的 AI 前端"变为"云盘 MCP 工具链中的一个分析服务"。

---

## 五、部署方案

### 统一 docker-compose

```yaml
services:
  mysql:      image:mysql:8.0        # 共享
  minio:      image:minio/minio      # 共享

  yliyun-cloud:     build:./cloud    ports:8080    # 云盘
  yliyun-mcp:       build:./mcp      ports:18100   # MCP Server
  mateclaw-server:  build:./mateclaw ports:18088   # AI 引擎
  wenshu-server:    build:./wenshu   port:18000    # 问数引擎
  wenshu-nginx:     build:./nginx    port:18080    # 问数前端
```

### Yliyun MCP Server Dockerfile

```dockerfile
# yliyun-mcp-server/Dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY tsconfig.json ./
COPY src/ ./src/
RUN pnpm build

# Stage 2: Runtime
FROM node:22-alpine
WORKDIR /app
RUN addgroup -S mcp && adduser -S mcp -G mcp
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY package.json ./
USER mcp
EXPOSE 18100
ENV NODE_ENV=production
ENV YLIYUN_API_BASE_URL=http://yliyun-cloud:8080
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:18100/health || exit 1
CMD ["node", "dist/index.js"]
```

### 外部访问架构（生产环境）

```yaml
# 生产环境通过 Nginx 反向代理暴露 MCP Server
# 支持外部 AI 客户端（Claude Desktop / Codex / Cursor 等）通过 HTTPS 访问
services:
  nginx:
    image: nginx:alpine
    ports:
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d
      - ./nginx/ssl:/etc/nginx/ssl
    config:
      # /mcp → yliyun-mcp:18100 (Streamable HTTP)
      # /api/ai → mateclaw-server:18088 (MateClaw API)
      # /wenshu → wenshu-nginx:18080 (WenShu)
```

### 服务间通信

| 调用方 | 被调用方 | 方式 | 地址(生产) | 地址(dev) |
|---|---|---|---|---|
| **外部 AI 客户端** | MCP Server | MCP Streamable HTTP + API Key | `https://ai.example.com/mcp` | `localhost:18100` |
| MateClaw → MCP | MCP Server | MCP Streamable HTTP + Ticket | `http://yliyun-mcp:18100` | `localhost:18100` |
| MCP → 云盘 | 云盘 API | 内部 HTTP + Ticket 验签 | `http://yliyun-cloud:8080` | `localhost:8080` |
| MateClaw → WenShu | WenShu | HTTP + Key | `http://wenshu-nginx:18080` | `localhost:18080` |
| 浏览器 → AI | MateClaw | HTTPS + JWT | `ai.example.com` | `localhost:18088` |

内部通信全部走 Docker 网络 DNS，不暴露到公网。开发环境统一 `localhost` 不同端口。

> **关键**：外部 AI 客户端（Claude Desktop / Codex / Cursor 等）通过 HTTPS 公网访问 MCP Server，需要配置 API Key 认证。内部 MateClaw 通过 Docker 内网直连，使用 Ticket 透传用户身份。

---

## 六、V1 实施路线图

> **三项目协同**：云盘 (saas) → MCP Server (yliyun-mcp-server) → MateClaw (yliyunclaw)

### 依赖关系

```
                          ┌─────────────────┐
                          │   MCP Server    │ ← 独立，先启动
                          │   14 个 Tool    │
                          └────────┬────────┘
                                   │ 提供云盘能力的 MCP 接口
                                   │
    ┌──────────────────────────────┼──────────────────────────────┐
    │                              │                              │
    ▼                              ▼                              ▼
┌──────────────┐          ┌──────────────┐           ┌──────────────┐
│ 云盘后端      │          │ MateClaw 后端 │           │ MateClaw 前端 │
│ ticket API   │          │ ticket 登录   │           │ @文件选择器   │
│ (0.5天)      │          │ 用户映射      │           │ 保存对话框    │
└──────┬───────┘          │ (2天)         │           │ (2.5天)       │
       │                  └──────┬───────┘           └──────┬───────┘
       │                         │                          │
       ▼                         │                          │
┌──────────────┐                 │                          │
│ 云盘前端      │                 │                          │
│ 导航+右键菜单 │                 │                          │
│ (2天)        │                 │                          │
└──────┬───────┘                 │                          │
       │                         │                          │
       └─────────────────────────┴──────────────────────────┘
                                 │
                                 ▼
                         ┌──────────────┐
                         │  集成联调     │
                         │  端到端测试   │
                         └──────────────┘
```

### 总工时估算

| 项目 | 后端 | 前端 | 小计 |
|---|---|---|---|
| **MCP Server** | 5天 | — | **5天** |
| **云盘** | 0.5天 | 2天 | **2.5天** |
| **MateClaw** | 2天 | 2.5天 | **4.5天** |
| **集成联调 + 测试** | — | — | **2天** |
| **总计** | | | **14天 (≈ 3周)** |

---

### 第 1 周：基础设施 + 核心能力

#### Sprint 1.1：MCP Server 完成（Day 1-5）⭐ 最先启动

> 项目路径：`/Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server/`
> 前置条件：云盘开发环境可访问（`http://localhost:8080`）
> **这是整个 V1 的基石，必须第一个完成。**

| Day | 任务 | 产出 | 验证方法 |
|---|---|---|---|
| **Day 1** | 项目启动：`pnpm install` → `pnpm dev` | 服务跑在 `localhost:18100`，`/health` 返回 200 | `curl localhost:18100/health` |
| **Day 1** | 云盘 HTTP Client：封装 `request()` 工具函数 | `POST /extends/user-token/get` 调通 | 用 curl 模拟调云盘 token API |
| **Day 2** | 认证模块：3 种认证方式全实现 | API Key / OAuth2 Token / MateClaw 内部头 | 分别用 3 种方式调 MCP endpoint |
| **Day 2-3** | 只读 Tools：`file.search` `file.list` `file.read` `file.versions` `space.context` `user.profile` | 6 个 Tool 可调 | MCP Inspector 逐个验证 |
| **Day 3-4** | 写入 Tools：`file.create` `file.save` `file.move` `file.delete` | 4 个 Tool 可调 | MCP Inspector 创建/更新/删除文件 |
| **Day 4** | 协作 Tools：`file.tag` `file.share_link` | 2 个 Tool 可调 | MCP Inspector 打标签/创建外链 |
| **Day 4** | 上下文工具：`file.grep` `file.summarize` | 2 个辅助 Tool | MCP Inspector 搜索关键词/获取摘要 |
| **Day 5** | 中间件 + 错误处理 + Docker 构建 | 速率限制/幂等/审计日志/结构化错误 | 压测 + 异常场景验证 |

**Day 5 验收标准**：
```bash
# 1. MCP Inspector 完整验证
npx @modelcontextprotocol/inspector --url http://localhost:18100/mcp
# 预期: tools/list 返回 14 个工具，每个可调通

# 2. 端到端 Tool 调用
curl -X POST http://localhost:18100/mcp \
  -H "Authorization: Bearer <test-token>" \
  -d '{"method":"tools/call","params":{"name":"file.search","arguments":{"query":"测试"}}}'
# 预期: 返回云盘中的文件列表

# 3. Docker 构建
docker build -t yliyun-mcp-server . && docker run -p 18100:18100 yliyun-mcp-server
# 预期: 容器启动，健康检查通过
```

---

#### Sprint 1.2：云盘后端 + MateClaw 后端（Day 3-5，与 MCP Server 并行）

> 这两个任务不依赖 MCP Server，可与 Sprint 1.1 并行。

##### 云盘后端（0.5天）

> 项目路径：`/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai/`

| 任务 | 文件 | 说明 |
|---|---|---|
| 新增 `POST /api/v1/ai/ticket` | `yly-module-saas` 或新建 controller | HMAC-SHA256 签名 JWT：`{userId, tenantId, nickname, exp=5min}` |
| 配置共享密钥 | `application.yml` | `yliyun.ai.ticket-secret: <随机32字节>` |

**验证**：`curl -X POST http://localhost:8080/api/v1/ai/ticket -H "Authorization: Bearer <cloud-jwt>"` → 返回 ticket JWT

##### MateClaw 后端（2天）

> 项目路径：`/Users/qinjinlong/Documents/projects/ai/yliyunclaw/mateclaw-server/`

| 任务 | 文件 | 说明 |
|---|---|---|
| 数据库迁移 | `V9008__yliyun_user_mapping.sql` | `mc_workspace_user` 加 `yliyun_user_id`、`yliyun_tenant_id` |
| Ticket 验证端点 | `YliyunAuthController.java` | `GET /api/v1/auth/yliyun/ticket?ticket=&redirect=` → 验签 → 映射用户 → 签发 JWT → 302 |
| 用户映射服务 | `YliyunUserMappingService.java` | `findOrCreate(yliyunUserId, tenantId, nickname)` → `McWorkspaceUser` |
| 共享密钥配置 | `application.yml` | `mateclaw.auth.yliyun.ticket-secret: <同云盘>` |

**验证**：浏览器访问 `http://localhost:18088/api/v1/auth/yliyun/ticket?ticket=<valid-ticket>&redirect=/chat` → 自动登录并跳转到聊天页

---

### 第 2 周：前端 + 集成

#### Sprint 2.1：云盘前端（Day 6-7）

> 项目路径：`/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-web-cloud-driver/`

| 任务 | 文件 | 说明 |
|---|---|---|
| 主导航 "AI助手" 按钮 | 导航栏组件 | 图标+文字，`@click="openAIAssistant()"` |
| 文件右键菜单 | 文件列表组件 | 单文件右键 → "🤖 AI助手分析" |
| 文件夹右键菜单 | 文件列表组件 | 文件夹右键 → "🤖 AI助手分析" |
| Ticket 生成 + 跳转 | `openAIAssistant()` 函数 | 调 `POST /api/v1/ai/ticket` → 新窗口打开 MateClaw |

```typescript
// 核心逻辑 (~30行)
async function openAIAssistant(context?: { fileId?: number; folderId?: number; folderName?: string }) {
  const { data: ticket } = await axios.post('/api/v1/ai/ticket');
  const params = new URLSearchParams({ ticket, redirect: '/chat' });
  if (context?.fileId) params.set('fileId', String(context.fileId));
  if (context?.folderId) {
    params.set('folderId', String(context.folderId));
    params.set('folderName', context.folderName || '');
  }
  window.open(`https://ai.example.com/?${params.toString()}`, '_blank');
}
```

**验证**：云盘登录 → 点击 "AI助手" → 新窗口打开 MateClaw → 自动登录 → 进入聊天页

---

#### Sprint 2.2：MateClaw 前端（Day 6-8）

> 项目路径：`/Users/qinjinlong/Documents/projects/ai/yliyunclaw/mateclaw-ui/`

| Day | 任务 | 文件 | 说明 |
|---|---|---|---|
| **Day 6** | 处理 ticket URL 参数 | `router/index.ts` 或 `App.vue` | 检测 `?ticket=` → 调 `/api/v1/auth/yliyun/ticket` → 获取 token → 自动登录 |
| **Day 6** | 处理 fileId/folderId 参数 | `ChatConsole.vue` | 进入聊天后自动调 MCP 加载文件/文件夹上下文 |
| **Day 7** | `CloudFilePicker.vue` | 新组件 | @ 触发 → 调 MCP `file.search` `file.list` → 下拉选择器（搜索+浏览） |
| **Day 7-8** | 保存到云盘对话框 | `SaveToCloudDialog.vue` | Tool Call 确认 → 浏览目录（MCP `file.list`）→ 确认文件名 → 保存（MCP `file.create`） |

**验证**：
- 访问 `/?ticket=xxx` → 自动登录
- 访问 `/?ticket=xxx&fileId=123` → 自动加载文件
- 输入 @ → 弹出云盘文件选择器 → 搜索/浏览文件 → 选中
- AI 生成内容 → 点击 "保存到云盘" → 选择目录 → 保存成功

---

### 第 3 周：联调 + 测试 + 收尾

#### Sprint 3.1：集成联调（Day 9-10）

> 三端都就绪后，逐场景验证。

**联调前置检查**：

```bash
# 1. 确认三端都在运行
curl http://localhost:8080/health     # 云盘
curl http://localhost:18100/health    # MCP Server
curl http://localhost:18088/health    # MateClaw

# 2. 确认 MateClaw 已配置 MCP Server
# MateClaw Admin → MCP Server 管理 → yliyun-mcp 状态为 "已连接"

# 3. 确认云盘 ticket 密钥和 MateClaw ticket 密钥一致
```

**逐场景联调**（按 TC-0 → TC-7 顺序）：

| 顺序 | 测试场景 | 关键验证点 |
|---|---|---|
| 1 | **TC-0: 免登录认证** | ticket 生成 → 验签 → 用户映射 → 自动登录 |
| 2 | **TC-1: 主导航进入** | 云盘点击 → MateClaw 自动登录 → 搜索文件 → 读文件 |
| 3 | **TC-2: 文件右键分析** | 右键 docx/xlsx/pdf → Agent 读取 → 正确回答 |
| 4 | **TC-3: 文件夹分析** | 右键文件夹 → Agent list → 按需 read → 跨文件回答 |
| 5 | **TC-4: @选择文件** | 输入 @ → 文件选择器 → 搜索 → 选中 → 基于文件问答 |
| 6 | **TC-5: 保存到云盘** | AI 生成 → 保存对话框 → 选目录 → 文件出现在云盘 |
| 7 | **TC-6: 权限安全** | 跨用户/跨租户访问被拒绝、速率限制生效 |
| 8 | **TC-7: 冒烟测试** | 完整流程 6 步一次通过 |

---

#### Sprint 3.2：文档 + 部署（Day 11-12）

| 任务 | 说明 |
|---|---|
| 更新云盘 README | 新增 AI 助手功能说明、ticket 密钥配置指南 |
| 更新 MateClaw README | 新增 yliyun 集成章节、MCP Server 配置指南 |
| 更新 MCP Server README | V1 功能列表、部署步骤、AI 客户端配置示例 |
| Docker Compose 整合 | 三端统一 `docker-compose.yml`（已有一版，需更新） |
| 生产环境密钥生成 | 生成并安全存储 ticket 共享密钥 |

---

### 项目路径速查

| 项目 | 路径 | 技术栈 |
|---|---|---|
| **云盘后端** | `/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai/` | Java 21 + Spring Boot 3.5 |
| **云盘前端** | `/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-web-cloud-driver/` | Vue 3 + TypeScript + Element Plus |
| **MCP Server** | `/Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server/` | TypeScript + FastMCP + Zod |
| **MateClaw 后端** | `/Users/qinjinlong/Documents/projects/ai/yliyunclaw/mateclaw-server/` | Java 21 + Spring Boot 3.5 |
| **MateClaw 前端** | `/Users/qinjinlong/Documents/projects/ai/yliyunclaw/mateclaw-ui/` | Vue 3 + TypeScript + Element Plus |
| **方案文档** | `/Users/qinjinlong/Documents/projects/ai/yliyunclaw/docs/yliyun-claw-wenshu-solution.md` | — |

### 下一步行动（按优先级）

1. **立即启动**：MCP Server Day 1（`pnpm install && pnpm dev`）
2. **本周并行**：云盘后端 ticket API + MateClaw 数据库迁移
3. **下周启动**：云盘前端改 UI + MateClaw 前端改 UI
4. **第三周**：三端联调 + 逐场景验收

---

## 七、MCP Server 测试与验收

### 7.1 测试金字塔

```
               ┌──────────┐
               │ E2E 测试  │  ← 真实 AI 客户端端到端验证
               │ (手动+脚本)│
               ├──────────┤
               │ 集成测试   │  ← MCP Inspector + 云盘 Mock API
               │           │
               ├──────────┤
               │ 单元测试   │  ← 每个 Tool 独立测试
               │           │
               └──────────┘
```

#### 单元测试（覆盖率目标 ≥ 85%）

每个 Tool 独立测试，Mock 云盘 API 返回：

```typescript
// tests/unit/tools/file-search.test.ts
import { describe, it, expect, vi } from 'vitest';

describe('file.search', () => {
  it('should return files matching query', async () => {
    mockCloudAPI.search.mockResolvedValue({ files: [...], total: 5 });
    const result = await executeTool('file.search', {
      query: '合同',
      maxResults: 10
    }, { userId: 'u1', tenantId: 't1' });
    expect(result.files).toHaveLength(5);
    expect(result.total).toBe(5);
  });

  it('should reject invalid fileType', async () => {
    await expect(executeTool('file.search', {
      query: 'test',
      fileType: 'executable' // 不在枚举中
    })).rejects.toMatchObject({ error: 'INVALID_PARAMETER' });
  });

  it('should enforce maxResults boundary', async () => {
    await expect(executeTool('file.search', {
      query: 'test',
      maxResults: 200 // 超过上限 100
    })).rejects.toMatchObject({ error: 'INVALID_PARAMETER' });
  });

  it('should pass tenantId filter to cloud API', async () => {
    await executeTool('file.search', { query: 'test' },
      { userId: 'u1', tenantId: 't1' });
    expect(mockCloudAPI.search).toHaveBeenCalledWith(
      expect.objectContaining({ tenantId: 't1' })
    );
  });
});
```

#### 集成测试（MCP Inspector）

使用 MCP 官方 Inspector 工具逐 Tool 验证：

```bash
# 启动 MCP Inspector 连接到 yliyun-mcp-server
npx @modelcontextprotocol/inspector \
  --transport streamable-http \
  --url http://localhost:18100/mcp \
  --headers '{"Authorization": "Bearer test_ticket_xxx"}'

# Inspector 提供 GUI 界面，可：
# 1. 查看所有注册的 Tools 列表
# 2. 手动填入参数调用每个 Tool
# 3. 查看 JSON-RPC 请求/响应原始数据
# 4. 验证 Schema 校验是否生效
```

**集成测试检查清单**：

| 测试项 | 验证点 | 通过标准 |
|---|---|---|
| `tools/list` 响应 | 返回 14 个工具，Schema 完整 | 工具名、描述、参数 Schema 均正确 |
| `file.search` 正常搜索 | 返回匹配文件列表 | 响应结构符合 Schema |
| `file.search` 空结果 | 搜索不存在的关键词 | 返回空列表 + `total: 0`，不报错 |
| `file.read` 文本文件 | 读取 .md 文件内容 | 返回 markdown 格式文本 |
| `file.read` 大文件截断 | 读取 >50KB 文件 | `truncated: true` + 字符数 = maxChars |
| `file.read` 不存在文件 | 读取已删除的文件ID | 返回 FILE_NOT_FOUND 错误 + suggestion |
| `file.list` 浏览根目录 | 列出根目录文件 | 返回 items 数组 + parentPath |
| `file.create` 创建文件 | 创建新文本文件 | 返回 fileId + url |
| `file.create` 幂等性 | 相同 idempotencyKey 调两次 | 第二次返回相同 fileId |
| `file.save` 更新文件 | 修改已有文件内容 | 返回新 versionId |
| `file.move` 移动文件 | 移到新路径 | oldPath + newPath 正确 |
| `file.delete` 移到回收站 | 删除文件（permanent=false） | recoveryAvailableUntil 不为空 |
| `file.delete` 拒绝永久删除 | 无确认时 permanent=true | 返回错误 |
| `file.tag` 添加标签 | add 操作 | 文件标签列表包含新标签 |
| `file.share_link` 创建分享 | 带密码+有效期 | 返回 shareUrl + expiresAt |
| `file.versions` 版本列表 | 查看文件版本历史 | 返回版本数组 |
| `space.context` 获取空间信息 | 不传 spaceId | 返回最近活跃空间信息 |
| `user.profile` 获取用户信息 | 获取当前用户 | 返回 userId + tenantId |
| `wenshu.analyze` 分析 Excel | 传 Excel 文件 + 问题 | 返回 answer + SQL + chartUrl |
| 认证失败 | 不带 Authorization 头 | 返回 401 |
| 权限不足 | 操作无权限的文件 | 返回 PERMISSION_DENIED + suggestion |
| 速率限制 | 短时间内 >100 次请求 | 返回 RATE_LIMITED + retryAfter |

#### E2E 测试（真实 AI 客户端）

手动测试 + 自动化脚本验证：

```bash
# 测试脚本示例：通过 MCP Client SDK 模拟 AI 调用
#!/bin/bash
# scripts/test-e2e.sh

MCP_URL="http://localhost:18100/mcp"
TICKET="test_ticket_xxx"

echo "=== 测试 1: Claude Desktop 场景 ==="
echo "用户: '帮我找一下上周的合同文件'"
# Agent 应调用 file.search(query="合同", dateFrom="...")

echo "=== 测试 2: Codex 场景 ==="
echo "用户: 'Save this document to the cloud drive'"
# Agent 应调用 file.create(name="document.md", content="...")

echo "=== 测试 3: MateClaw 场景 ==="
echo "用户: '分析这个销售Excel的季度趋势'"
# Agent 应调用 file.search → 找到文件 → wenshu.analyze
```

### 7.2 多客户端兼容性测试矩阵

> **这是发布前必须全部通过的测试矩阵**。每个客户端对 MCP 协议的实现有细微差异，必须在真实环境中验证。

| 测试场景 | Claude Desktop | Codex | Cursor | Windsurf | ChatGPT | MateClaw |
|---|---|---|---|---|---|---|
| **MCP 连接建立** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **tools/list 正常返回** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **file.search 搜索文件** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **file.read 读取内容** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **file.create 创建文件** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **file.save 保存更新** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **多步操作链** (search→read→save) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **错误处理** (文件不存在) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **权限错误** (无写权限) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **大文件处理** (>10MB) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **并发调用** (同时读多个文件) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **断线重连** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| **认证过期重新认证** | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

#### 各客户端配置参考

**Claude Desktop** (`~/Library/Application Support/Claude/claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "yliyun": {
      "type": "streamable-http",
      "url": "https://ai.example.com/mcp",
      "headers": {
        "Authorization": "Bearer ${YLIYUN_API_KEY}"
      }
    }
  }
}
```

**Codex** (`~/.codex/mcp.json`):
```json
{
  "mcpServers": {
    "yliyun": {
      "transport": "streamable-http",
      "url": "https://ai.example.com/mcp",
      "env": {
        "YLIYUN_API_KEY": "your-api-key"
      },
      "headers": {
        "Authorization": "Bearer ${YLIYUN_API_KEY}"
      }
    }
  }
}
```

**Cursor** (`~/.cursor/mcp.json`):
```json
{
  "mcpServers": {
    "yliyun": {
      "transport": "streamable-http",
      "url": "https://ai.example.com/mcp",
      "headers": {
        "Authorization": "Bearer your-api-key"
      }
    }
  }
}
```

**MateClaw** (管理界面配置):
```yaml
name: yliyun-mcp
transport: streamable_http
url: http://yliyun-mcp:18100/mcp
headers:
  X-Internal-Service: mateclaw
  X-Forward-User-Id: ${currentUserId}
  X-Forward-Tenant-Id: ${currentTenantId}
disclosureTier: full
```

### 7.3 性能基准

| 指标 | 目标值 | 测量方式 |
|---|---|---|
| `tools/list` 响应时间 | < 100ms | P95 |
| `file.search` 响应时间 | < 500ms | P95（1000 万文件索引） |
| `file.read` 响应时间 | < 200ms | P95（1MB 文件） |
| `file.create` 响应时间 | < 300ms | P95 |
| 并发处理能力 | ≥ 100 req/s | 压测 10 并发用户 |
| 内存占用（空闲） | < 150MB | Docker stats |
| 内存占用（满负载） | < 512MB | Docker stats |
| 冷启动时间 | < 3s | 容器启动到首次响应 |

### 7.4 验收标准

#### 功能验收

- [ ] 14 个 MCP Tool 全部实现，参数 Schema 完整
- [ ] 所有 Tool 通过 MCP Inspector 集成测试
- [ ] Ticket 兑票认证流程完整可用
- [ ] API Key 生成/管理/撤销功能可用
- [ ] 权限校验：无权限用户调用写操作时正确拒绝
- [ ] 租户隔离：无法跨 tenantId 访问文件
- [ ] 幂等性：写操作重复调用不产生副作用
- [ ] 结构化错误：所有错误返回包含 `suggestion` 字段
- [ ] 审计日志：每次 Tool 调用均记录（含用户、参数、结果）

#### 兼容性验收

- [ ] Claude Desktop：完整文件 CRUD 场景通过
- [ ] Codex / Cursor：文件读写场景通过
- [ ] MateClaw：Agent 可调用所有 MCP Tool
- [ ] MateClaw YliyunPlugin：上下文注入 + 快捷指令正常

#### 性能验收

- [ ] 所有工具在目标延迟范围内
- [ ] 100 req/s 并发压测 5 分钟无错误
- [ ] 内存稳定在 512MB 以内，无泄漏

#### 安全验收

- [ ] OWASP Top 10 安全检查通过
- [ ] 路径遍历攻击测试通过（`../` 等被拒绝）
- [ ] SQL/命令注入测试通过（参数被正确转义）
- [ ] 认证绕过测试通过（无有效 token 时拒绝所有请求）
- [ ] 速率限制生效（超过限额后被拒绝）

---

## 八、关键风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|
| **云盘 API 不稳定** | 中 | 高 | MCP Server 端加重试（3次）+ 熔断器；返回结构化错误告知 AI 而非抛异常 |
| **MCP 协议变更** | 低 | 高 | 关注 2026-07-28 重大变更（session 移除等）；使用 FastMCP 框架由社区吸收变更 |
| **AI 模型不理解 Tool 语义** | 中 | 中 | 每个 Tool 写详细的 AI 友好描述；在 description 中包含使用场景和提示 |
| **跨客户端兼容性问题** | 中 | 中 | 早期在目标客户端上测试；持续跟踪各客户端 Changelog |
| **大文件传输性能** | 中 | 低 | 文件内容通过云盘直链下载，MCP 只传元数据；内容读取限制 maxChars |
| **TypeScript 团队缺口** | 中 | 高 | 备选 Java/Quarkus 方案；TypeScript SDK 学习曲线低（≤1周） |
| **权限同步延迟** | 低 | 高 | 不缓存权限，每次调用实时验权；接受 20-50ms 额外延迟 |
| **WenShu 接口变更** | 低 | 中 | wenshu.analyze 作为独立 Bridge 模块，变更时只改这一处 |

---

## 九、关键参考

### MCP 协议规范
- [MCP Specification (2025-11-25)](https://modelcontextprotocol.io/specification/2025-11-25/basic/index)
- [MCP Architecture Overview](https://modelcontextprotocol.io/docs/learn/architecture)
- [MCP Client Best Practices](https://modelcontextprotocol.io/docs/develop/clients/client-best-practices)
- [MCP Inspector Tool](https://modelcontextprotocol.io/docs/tools/inspector)

### 2026 年重大协议变更（需关注）
- **2026-07-28 发布**：Session 机制移除 (SEP-2567)、Initialize 握手移除 (SEP-2575)、错误码调整 (SEP-2164)、新路由头 (SEP-2243)
- 建议在 FastMCP 框架升级后回归测试所有客户端

### MCP 生态工具
- [FastMCP](https://github.com/punkpeye/fastmcp) — TypeScript MCP Server 框架（推荐）
- [MCP-Framework](https://mcp-framework.com/) — TypeScript 脚手架工具
- [Quarkus MCP Server SDK](https://docs.quarkiverse.io/quarkus-mcp-server/dev/) — Java MCP Server 框架（备选）
- [FastAPI-MCP](https://github.com/tadata-org/fastapi_mcp) — Python 零配置 MCP 集成

### 安全参考
- [MCP Security Best Practices (Microsoft)](https://github.com/microsoft/mcp-for-beginners/blob/main/05-AdvancedTopics/mcp-security/README.md)
- [OWASP Top 10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)

---

## 十、云盘现有 API 盘点与 MCP Tool 映射

> 基于对 yly-saas-cdms-ai 项目（yudao-cloud 框架，Spring Boot 3 + Java 21）的深入分析。
> 项目路径：`/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai/`

### 10.1 云盘技术架构概览

```
yly-saas-cdms-ai/
├── yudao-server                  # 主服务入口（Spring Boot）
├── yudao-framework               # 框架层（安全、ORM、MQ、文件存储）
├── yudao-module-system           # 系统模块（用户、租户、OAuth2、权限）
│   ├── AuthController            # POST /system/auth/login, logout, refresh-token
│   └── OAuth2OpenController      # POST /system/oauth2/token, /check-token ⭐
├── yudao-module-infra            # 基础设施（文件存储、配置）
├── yliyun-module-cloud-drive     # 云盘核心模块 ⭐
│   ├── CloudFileController       # GET/POST /cloud-drive/file/*
│   ├── CloudFileVersionController # /cloud-drive/file-version/*
│   ├── CloudFileShareLinkController # /cloud-drive/share-link/*
│   ├── CloudFileTagController    # /cloud-drive/file-tag/*
│   ├── CloudDriveSpaceController # /cloud-drive/space/*
│   └── ...（收藏、回收站、备注、钉选等）
├── yliyun-module-extends         # 扩展模块
│   └── ExtendsUserTokenController # POST /extends/user-token/get ⭐⭐⭐
├── yliyun-module-nas             # NAS 集成模块
├── yliyun-module-wenshu          # WenShu 问数模块（仅有 target，源码可能独立仓库）
└── yly-module-saas               # SaaS 多租户计费模块
```

### 10.2 核心数据模型

| 表名 | 对应 DO | 说明 |
|---|---|---|
| `cloud_file_index` | `CloudFileIndexDO` | 文件索引（source/bucket/path/name/size/mime/md5/空间/父ID/owner/状态） |
| `cloud_file_object` | `CloudFileObjectDO` | 文件存储对象（引用 infra_file_config 的物理存储） |
| `cloud_file_tree` | `CloudFileTreeDO` | 文件树闭包表（ancestor/descendant/depth，支持快速子树查询） |
| `cloud_file_version` | `CloudFileVersionDO` | 文件版本历史 |
| `cloud_file_share_link` | `CloudFileShareLinkDO` | 外链分享（shareCode/extractCode/expireTime/密码/访问次数/预览/下载权限） |
| `cloud_file_tag` / `cloud_file_tag_rel` | `CloudFileTagDO` / `CloudFileTagRelDO` | 标签体系 + 文件夹联 |
| `cloud_file_favorite` | `CloudFileFavoriteDO` | 收藏夹 |
| `cloud_file_recycle` | `CloudFileRecycleDO` | 回收站 |
| `cloud_file_recent` | `CloudRecentFileDO` | 最近访问 |
| `cloud_file_pin` | `CloudFilePinDO` | 文件钉选 |
| `cloud_file_remark` | `CloudFileRemarkDO` | 文件备注 |
| `cloud_user_quota` | `CloudUserQuotaDO` | 用户配额 |
| `cloud_storage_config` | `CloudStorageConfigDO` | 存储配置（LOCAL/S3/NAS） |
| `folder_permission` | `FolderPermissionDO` | 文件夹权限 |

### 10.3 云盘 API → MCP Tool 映射表

#### 认证相关 API（系统模块）

| 云盘 API | 方法 | 说明 | MCP 使用场景 |
|---|---|---|---|
| `/system/auth/login` | POST | 账号密码登录 | ❌ MCP 不直接使用（用户已在云盘登录） |
| `/system/auth/refresh-token` | POST | 刷新令牌 | ❌ 客户端自行处理 |
| `/system/oauth2/token` | POST | OAuth2 获取令牌 | ⚠️ 远期可用于 OAuth flow |
| `/system/oauth2/check-token` | POST | 校验令牌有效性 | ✅ **MCP Server 认证中间件调用** |

#### 第三方 Token API（扩展模块）⭐ 关键

| 云盘 API | 方法 | 说明 | MCP 使用场景 |
|---|---|---|---|
| `/extends/user-token/get` | POST | 为指定租户用户生成 OAuth2 令牌 | ✅ **Ticket 兑票核心：MCP Server 通过此 API 将 ticket 转换为 OAuth2 token** |

> **参数**：`tenantId`（必填）、`userId`/`username`/`mobile`/`source`（选一）、`appKey`（白名单校验）
> **返回**：`token`、`userId`、`nickname`、`expiresTime`

#### 文件操作 API → MCP Tools

| 云盘 API | MCP Tool | 映射说明 |
|---|---|---|
| `GET /cloud-drive/file/list` | `file.search` / `file.list` | keyword + tagId + recursive + fileType 过滤 |
| `GET /cloud-drive/file/preview` | `file.read` (元数据) | 返回文件详情（不含内容） |
| `GET /cloud-drive/file/content` | `file.read` (内容) | 需要 previewToken，返回文件流 |
| `GET /cloud-drive/file/summary` | `file.search` (统计) | 文件汇总统计 |
| `POST /cloud-drive/file/folder` | `file.create` (目录) | 创建文件夹 |
| `POST /cloud-drive/file/upload` | `file.create` (文件) | 上传文件（支持分片） |
| `PUT /cloud-drive/file/rename` | `file.move` (重命名) | 重命名 |
| `DELETE /cloud-drive/file/delete` | `file.delete` | 删除（移入回收站，异步） |
| `PUT /cloud-drive/file/move` | `file.move` | 移动（异步） |
| `POST /cloud-drive/file/copy` | `file.create` (复制) | 复制（异步） |
| `GET /cloud-drive/file/download` | `file.read` (下载) | 下载文件（支持 Range） |
| `POST /cloud-drive/file/permissions/resolve` | （内部使用） | 批量权限解析 |

#### 版本 API

| 云盘 API | MCP Tool | 映射说明 |
|---|---|---|
| `GET /cloud-drive/file-version/list` | `file.versions` | 版本列表 |
| `GET /cloud-drive/file-version/download` | `file.versions` (下载) | 下载指定版本 |
| `POST /cloud-drive/file-version/restore` | `file.versions` (恢复) | 恢复历史版本 |
| `POST /cloud-drive/file-version/upload` | `file.save` (新版本) | 上传新版本 |

#### 分享 API

| 云盘 API | MCP Tool | 映射说明 |
|---|---|---|
| `POST /cloud-drive/share-link/create` | `file.share_link` | 创建外链 |
| `PUT /cloud-drive/share-link/update` | `file.share_link` (更新) | 编辑外链 |
| `PUT /cloud-drive/share-link/invalidate` | `file.share_link` (失效) | 失效外链 |

#### 标签 API

| 云盘 API | MCP Tool | 映射说明 |
|---|---|---|
| `PUT /cloud-drive/file-tag/rel/save` | `file.tag` | 保存文件标签绑定 |
| `GET /cloud-drive/file-tag/rel/list` | `file.list` (含标签) | 获取文件标签 |
| `GET /cloud-drive/file-tag/list` | （辅助） | 获取用户标签列表 |

#### 空间与用户 API

| 云盘 API | MCP Tool | 映射说明 |
|---|---|---|
| `GET /cloud-drive/space/dept-list` | `space.context` (部分) | 部门空间列表 |

### 10.4 现有 AI 集成基础

云盘前端已具备 AI 集成基础：

- **`CloudDriveAssistantPanel.vue`**：云盘内嵌 AI 助手面板，支持 AUTO/CHAT/TOOL 三种模式
- **Drive Assistant API**：`/ai/drive-assistant/execute-stream`（SSE 流式响应，带工具调用）
- **MCP 支持**：ChatRole 可绑定 `mcpClientNames`，前端已展示 MCP 工具数量
- **RAG 管线**：知识库 + 文档分段 + ES 向量检索
- **多模型支持**：OpenAI、DeepSeek、Ollama、通义千问等 11+ 平台

> **关键发现**：云盘已有 AI 工具调用（Tool Calling）的完整通道。MCP Server 将在此基础上提供标准化协议，让外部 AI（Claude/Codex 等）也能调用云盘能力。

---

## 十一、V1 vs V2 实施策略

> **核心原则：V1 优先打通业务闭环，V2 完善接口体验。**

### 11.1 V1 业务打通（本期）— 全用现有 API

V1 的目标是让 **claw → MCP → 云盘** 整条链路跑通，用户可以：
1. 从云盘打开 AI 助手 → MateClaw 自动获得用户身份
2. 在 MateClaw 中搜索/浏览/读取云盘文件
3. AI 处理后保存结果回云盘

**V1 使用现有云盘 API，MCP Server 侧自行处理文本提取和格式转换。**

| MCP Tool | V1 实现方式 | 使用的云盘 API | 降级策略 |
|---|---|---|---|
| `file.search` | 递归搜索 | `GET /cloud-drive/file/list?keyword=&recursive=true` | ✅ 直接用 |
| `file.read` | MCP Server 下载文件 → 本地提取文本 | `GET /cloud-drive/file/download` | ✅ 用 download API 拿到 bytes，MCP Server 用 Tika/pdf-parse 提取文本 |
| `file.list` | 目录浏览 | `GET /cloud-drive/file/list` | ✅ 直接用 |
| `file.create` | 创建目录 + multipart 上传 | `POST /cloud-drive/file/folder` + `POST /cloud-drive/file/upload` | ✅ 直接用（MCP Server 将文本内容转 Buffer 后上传） |
| `file.save` | 上传新版本 | `POST /cloud-drive/file-version/upload` | ✅ 直接用 |
| `file.move` | 移动 + 重命名 | `PUT /cloud-drive/file/move` + `PUT /cloud-drive/file/rename` | ✅ 直接用 |
| `file.delete` | 移到回收站 | `DELETE /cloud-drive/file/delete` | ✅ 直接用 |
| `file.tag` | 标签绑定 | `PUT /cloud-drive/file-tag/rel/save` | ✅ 直接用 |
| `file.share_link` | 创建外链 | `POST /cloud-drive/share-link/create` | ✅ 直接用 |
| `file.versions` | 版本列表 | `GET /cloud-drive/file-version/list` | ✅ 直接用 |
| `space.context` | 部门空间列表 | `GET /cloud-drive/space/dept-list` | ⚠️ 功能受限（仅有空间列表，无统计） |
| `user.profile` | 从认证上下文返回 | `/extends/user-token/get` 返回的 nickname | ⚠️ 功能受限（无存储统计） |

> **关键决策**：V1 的 `file.read` 不走云盘 text-content API（不存在），改为 MCP Server 下载文件后本地提取文本。这需要 MCP Server 集成 Apache Tika / pdf-parse 等文本提取库。

### 11.2 V2 接口补全（后续）— 云盘侧新增 API

V2 将这些 Workaround 替换为专用 API，提升性能和体验：

| 优先级 | 新增 API | 解决的 V1 痛点 | 工时 |
|---|---|---|---|
| **V2-P0** | `GET /cloud-drive/file/text-content` | MCP Server 不需要下载大文件再提取文本；云盘侧直接返回提取结果 | 2天 |
| **V2-P0** | `POST /cloud-drive/file/write-content` | MCP Server 不需要构造 multipart；直接传文本内容 | 1.5天 |
| **V2-P1** | `GET /cloud-drive/file/search` | 真正的全局搜索（跨目录、按 MIME 过滤），而非依赖递归 list | 1.5天 |
| **V2-P1** | `GET /cloud-drive/space/context` | 空间统计、最近活动等上下文信息 | 1天 |
| **V2-P1** | `GET /extends/user/profile` | 用户云盘使用统计、配额信息 | 0.5天 |
| **V2-P2** | WenShu 对接 | `wenshu.analyze` 完整能力 | 2-3天 |

> **2026-07-29 代码审计状态：未实施。** 当前云盘后端未发现上述五个专用 API；MCP `file.search` 仍使用递归目录列表，文本读写仍组合预览/下载/上传接口，manifest 中也没有 `wenshu.analyze`。云盘已有的 `preview`、`preview-stream-url`、`content`、`download` 和分享接口属于 V1 可组合能力，不等同于 V2 设计已完成。

### 11.3 V1 云盘侧需要的 zero-change 确认项

以下现有 API 在 V1 中被 MCP Server 使用，**不需要改云盘代码**，但需要确认：

| 确认项 | API | 确认内容 |
|---|---|---|
| Token 生成 | `POST /extends/user-token/get` | MCP Server 的 appKey 是否已在云盘白名单中 |
| Token 校验 | `POST /system/oauth2/check-token` | 生成的 token 是否能正常校验通过 |
| 文件下载 | `GET /cloud-drive/file/download` | MCP Server（服务端）调用时能否正常返回文件流（不含用户交互） |
| 文件上传 | `POST /cloud-drive/file/upload` | MCP Server 调用时 multipart 上传是否有限制（文件大小、频率） |
| 递归搜索 | `GET /cloud-drive/file/list?recursive=true` | 确认 recursive 参数已启用，搜索结果包含子目录文件 |
| 外链创建 | `POST /cloud-drive/share-link/create` | MCP Server 调用是否受 session 限制 |

### 11.4 V2 云盘侧补全 API 设计（参考，V2 实施时使用）

> ⚠️ 以下 API 设计为 V2 参考。V1 不需要实现，MCP Server 使用现有 API 降级方案。

#### 11.4.1 文件文本内容读取 API（V2）

```java
// CloudFileController.java 新增
@GetMapping("/text-content")
@Operation(summary = "获取文件文本内容（供 AI/MCP 使用）")
public CommonResult<CloudFileTextContentRespVO> getTextContent(
    @RequestParam("id") Long id,
    @RequestParam(value = "maxChars", defaultValue = "50000") Integer maxChars) {
    return success(cloudFileService.getTextContent(id, maxChars, getRequiredLoginUserId()));
}

// 返回结构
class CloudFileTextContentRespVO {
    String content;          // 提取的文本内容（Markdown 格式）
    String fileName;
    String mimeType;
    Integer charCount;
    Boolean truncated;       // 是否截断
    Map<String, Object> metadata; // 作者、创建时间、页数、Sheet 名等
}
```

**实现要点**：
- 使用 Tika/Apache POI 提取 Office 文档文本
- PDF 使用 PDFBox 提取文本
- 图片调用 OCR 服务（已有 tesseract-ocr 依赖）
- 纯文本/Markdown 直接返回
- 超过 maxChars 截断并标记

#### 11.4.2 文件文本写入 API（V2）

```java
// CloudFileController.java 新增
@PostMapping("/write-content")
@Operation(summary = "写入文本内容到文件（供 AI/MCP 使用）")
@ApiAccessLog(operateType = OperateTypeEnum.CREATE)
public CommonResult<CloudFileRespVO> writeContent(
    @Valid @RequestBody CloudFileWriteContentReqVO reqVO) {
    return success(cloudFileService.writeContent(reqVO, getRequiredLoginUserId()));
}

class CloudFileWriteContentReqVO {
    Long fileId;              // 更新已有文件时提供（与 parentId+fileName 二选一）
    Long parentId;            // 新建文件时的父目录
    String fileName;          // 新建文件时的文件名
    String content;           // 文本内容
    String mimeType;          // 如 text/markdown, text/plain
    Boolean createVersion;    // 是否创建新版本
    String versionNote;       // 版本说明
    String idempotencyKey;    // 幂等键
}
```

#### 11.4.3 全局文件搜索 API（V2）

```java
// CloudFileController.java 新增
@GetMapping("/search")
@Operation(summary = "全局搜索文件（供 AI/MCP 使用）")
public CommonResult<CloudFileSearchRespVO> searchFiles(
    @Valid CloudFileSearchReqVO reqVO) {
    return success(cloudFileService.searchFiles(reqVO, getRequiredLoginUserId()));
}

class CloudFileSearchReqVO {
    String query;             // 搜索关键词
    List<String> fileTypes;   // document/spreadsheet/image/pdf/video/all
    List<Long> tagIds;        // 标签过滤（AND 逻辑）
    String dateFrom;          // ISO 8601
    String dateTo;            // ISO 8601
    Integer maxResults;       // 默认 20，最大 100
}
```

#### 11.4.4 用户信息 API（V2）

```java
// ExtendsUserTokenController 或新 Controller
@GetMapping("/extends/user/profile")
@Operation(summary = "获取第三方用户信息")
public CommonResult<UserProfileRespVO> getUserProfile() {
    Long userId = getLoginUserId();
    // 返回用户信息 + 云盘使用统计
}
```

#### 11.4.5 空间上下文 API（V2）

```java
// CloudDriveSpaceController.java 新增
@GetMapping("/cloud-drive/space/context")
@Operation(summary = "获取空间上下文信息（供 AI 使用）")
public CommonResult<SpaceContextRespVO> getSpaceContext(
    @RequestParam(value = "spaceId", required = false) Long spaceId) {
    return success(cloudDriveSpaceService.getSpaceContext(spaceId, getRequiredLoginUserId()));
}
```


---

## 十二、V1 核心需求与交互方案

> **目标**：端到端打通云盘 → MCP → MateClaw 业务闭环，5 个核心交互场景可演示。

### 12.1 V1 核心需求总览

```
                        ┌─────────────────────────┐
                        │     一粒云云盘 (主入口)    │
                        │  主导航 + 右键菜单 + 文件浏览 │
                        └──────────┬──────────────┘
                                   │
            ┌──────────────────────┼──────────────────────┐
            │                      │                      │
            ▼                      ▼                      ▼
    场景1: 免登录进入        场景2: 文件AI分析       场景3: 文件夹AI分析
    点击AI助手按钮          右键→AI助手分析         右键→AI助手分析
    自动登录MateClaw        文件作附件解析会话       遍历文件→创建KB→问答
            │                      │                      │
            └──────────────────────┼──────────────────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │   MateClaw AI 引擎   │
                        │   Agent + Wiki + KB  │
                        └──────────┬──────────┘
                                   │
                         ┌─────────┴─────────┐
                         │                   │
                         ▼                   ▼
                  场景4: @选择文件      场景5: 保存到云盘
                  通过MCP浏览云盘       AI生成文件→存回云盘
                  选文件作会话上下文     指定路径+文件名
```

### 12.2 免登录 Ticket 机制设计

#### 核心原理

云盘和 MateClaw 各自有独立的用户体系和 JWT 认证。通过 **一次性 ticket 兑票** 机制实现免登录跳转：

```
云盘用户 (已登录)
    │
    │ 1. 点击"AI助手"
    ▼
云盘后端生成 ticket
  (HMAC签名JWT: userId, tenantId, nickname, exp=5min)
    │
    │ 2. 重定向: https://ai.example.com/?ticket=xxx&redirect=/chat
    ▼
MateClaw 接收 ticket
    │
    │ 3. 验签 → 查找/创建映射用户 → 签发 MateClaw JWT
    ▼
MateClaw 自动登录 → 进入会话页
```

#### Ticket 数据结构

```json
{
  "sub": "yliyun-ticket",
  "userId": 12345,
  "tenantId": 1,
  "nickname": "张三",
  "iat": 1730000000,
  "exp": 1730000300,
  "jti": "unique-ticket-id"
}
```

#### 各端改动

| 端 | 改动内容 | 工时 |
|---|---|---|
| **云盘前端** | 主导航加"AI助手"按钮，文件/文件夹右键菜单加"AI助手分析"，点击时调后端生成 ticket 并跳转 | 1天 |
| **云盘后端** | 新增 `POST /api/v1/ai/ticket`：用共享密钥 HMAC-SHA256 签名生成 JWT ticket | 0.5天 |
| **MateClaw** | 新增 `GET /api/v1/auth/yliyun/ticket?ticket=&redirect=`：验签 → 映射用户 → 签发 MateClaw JWT → 302 跳转 | 1.5天 |
| **MCP Server** | 无需改动（ticket 验签在 MateClaw 侧完成） | 0天 |

#### MateClaw 用户映射逻辑

```java
// 伪代码
public String handleTicket(String ticket, String redirect) {
    // 1. 验签
    TicketPayload payload = verifyTicketSignature(ticket, yliyunSharedSecret);
    
    // 2. 查找已有映射
    McWorkspaceUser user = userMapper.selectByYliyunUserId(payload.getUserId());
    
    // 3. 不存在则创建
    if (user == null) {
        user = createUser(payload.getUserId(), payload.getTenantId(), payload.getNickname());
    }
    
    // 4. 更新昵称（可能变更）
    user.setNickname(payload.getNickname());
    userMapper.updateById(user);
    
    // 5. 签发 MateClaw JWT
    String jwt = jwtService.createToken(user.getId(), user.getWorkspaceId());
    
    // 6. 302 跳转（JWT 写入 cookie 或 URL fragment）
    return "redirect:" + redirect + "#token=" + jwt;
}
```

> **安全要点**：ticket 有效期为 5 分钟，一次性使用（Redis 记录 jti），共享密钥定期轮换。

---

### 12.3 五大交互场景详细设计

#### 场景 1：主导航进入 AI 助手（免登录）

```
┌──────────────────────────────────────────────────────────────┐
│  云盘 Web UI                                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  [文件] [共享] [收藏] [回收站] ...      [🔍] [AI助手🤖] │ │  ← 新增按钮
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  点击 "AI助手" →                                             │
│    1. 调云盘后端 POST /api/v1/ai/ticket                      │
│    2. 获得 ticket JWT                                        │
│    3. 新窗口打开:                                            │
│       https://ai.example.com/?ticket=xxx&redirect=/chat      │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  MateClaw (新窗口)                                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  验证 ticket → 创建/查找用户 → 签发 JWT → 进入 /chat    │ │
│  │                                                         │ │
│  │  🤖 AI 助手                              [用户: 张三]   │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 你好，我是你的云盘AI助手。可以帮你：               │    │ │
│  │  │ • 搜索和分析云盘文件                              │    │ │
│  │  │ • 总结文档内容                                   │    │ │
│  │  │ • 根据文件内容回答你的问题                        │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 💬 输入你的问题...                        [发送] │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

**数据流**：
1. 云盘前端 → `POST /api/v1/ai/ticket`（带云盘 JWT）
2. 云盘后端 → 生成 HMAC 签名 ticket JWT
3. 浏览器 → 新窗口打开 `https://ai.example.com/?ticket=<jwt>&redirect=/chat`
4. MateClaw → `GET /api/v1/auth/yliyun/ticket` 验签 + 用户映射
5. MateClaw → 302 跳转到 `/chat`，JWT 写入 cookie
6. MateClaw → 前端加载会话页，调用 MCP `space.context` + `user.profile` 获取上下文

---

#### 场景 2：文件右键 → AI 助手分析

```
┌──────────────────────────────────────────────────────────────┐
│  云盘 Web UI                                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  文件名                         大小      修改时间       │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ 📄 Q3销售报告.xlsx           2.3MB    07-15      │   │ │
│  │  │       ┌──────────────────┐                       │   │ │
│  │  │       │  打开             │                      │   │ │
│  │  │       │  下载             │                      │   │ │
│  │  │       │  分享             │                      │   │ │
│  │  │       │  重命名           │                      │   │ │
│  │  │       │  🤖 AI助手分析 →  │  ← 新增菜单项         │   │ │
│  │  │       └──────────────────┘                       │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  点击 "AI助手分析" →                                         │
│    1. 调 POST /api/v1/ai/ticket (附加 fileId=xxx)            │
│    2. 新窗口打开:                                            │
│       https://ai.example.com/?ticket=xxx&fileId=12345        │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  MateClaw (新窗口)                                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  🤖 AI 助手                                         │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 📎 已加载文件: Q3销售报告.xlsx (2.3MB)           │    │ │
│  │  │                                                 │    │ │
│  │  │ 🤖 我看到了"Q3销售报告.xlsx"。你可以问我:         │    │ │
│  │  │   • 总结这份报告的主要内容                        │    │ │
│  │  │   • 分析Q3的销售趋势                             │    │ │
│  │  │   • 对比各产品的销售数据                          │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 💬 "总结这份报告的核心要点"                [发送] │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  处理流程:                                                   │
│    1. 收到 fileId=12345                                     │
│    2. Agent 调 MCP file.read(fileId=12345)                  │
│    3. MCP Server → download file → extract text → return    │
│    4. 文件内容作为 system prompt 注入会话                     │
│    5. 用户开始基于文件内容问答                                │
└──────────────────────────────────────────────────────────────┘
```

**MateClaw 端具体实现**：

```typescript
// ChatConsole.vue 或 route guard 中处理
async function handleFileContext(fileId: number) {
  // 1. 通过 MCP 获取文件信息
  const fileInfo = await mcpClient.callTool('file.read', { fileId });
  
  // 2. 构造会话初始消息
  const systemContext = `
[已加载云盘文件: ${fileInfo.fileName}]

文件内容:
${fileInfo.content}

${fileInfo.truncated ? '(内容已截断)' : ''}

请基于以上文件内容回答用户的问题。`;
  
  // 3. 创建会话并注入上下文
  await chatService.createSession({
    initialMessage: `我打开了云盘文件"${fileInfo.fileName}"`,
    systemContext,
  });
}
```

---

### 12.3 主流 Agent 如何处理"文件夹/多文件问答"

> **问题**：对云盘文件夹做 AI 问答，方案 A 是"导入为持久化知识库"，但文件夹内容随时变化（新增/修改/删除），维护同步极其复杂。主流 Agent 怎么做？

#### Claude Desktop / Codex / Cursor 的做法

这些 Agent 本质上是 **ReAct 模式**（推理-行动循环）。处理文件夹问答时：

```
用户: "帮我分析项目文档文件夹里的内容"
  │
  ▼
Agent 思考: 我需要先了解文件夹里有什么
  → 调用 MCP file.list(parentId=678, recursive=true)
  → 获得文件列表: [PRD.md, API设计.docx, 数据库设计.md, ...]
  │
  ▼
Agent 思考: 用户想了解项目，我先看 PRD 和架构文档
  → 调用 MCP file.read(fileId=PRD的id)
  → 调用 MCP file.read(fileId=架构文档的id)
  → 总结内容，回答用户
  │
  ▼
用户追问: "数据库表结构怎么设计的?"
  │
  ▼
Agent 思考: 这应该在看 数据库设计.md 这个文件
  → 调用 MCP file.read(fileId=数据库设计的id)  ← 实时读取，始终是最新内容
  → 回答表结构
```

**核心原则**：
- **不建索引，不建 KB**。文件夹本身就是"索引"——`file.list` 就是目录
- **按需读取**。Agent 根据用户问题，自主决定读哪些文件
- **始终最新**。每次 `file.read` 都是实时读取，天然同步
- **上下文窗口是上限**。如果文件总内容超出窗口，Agent 会选择性读取

#### ChatGPT / GPT Actions 的做法

同样基于 tool calling，加上 **渐进式检索**：
- `file.list` 先获取文件摘要（名称、大小、日期）
- Agent 根据文件名推断相关性，只读取匹配的文件
- 对超大文件，先 `file.read(maxChars=5000)` 预览，确认相关后再读全文

#### RAG 知识库的适用场景（对比）

| 场景 | 文件夹问答（MCP 实时读取） | 知识库（RAG/KB） |
|---|---|---|
| **文件夹内容频繁变化** | ✅ 始终最新 | ❌ 需同步，成本高 |
| **文件数量多（>100）** | ⚠️ Agent 需多轮推理 | ✅ 向量检索快速定位 |
| **需要跨文件语义关联** | ⚠️ 依赖 Agent 推理能力 | ✅ Embedding 召回更准 |
| **文档量巨大（>10MB）** | ❌ 超出上下文窗口 | ✅ 分段索引 |
| **静态参考文档** | ✅ 可用 | ✅ 更适合 |
| **维护成本** | ✅ 零维护 | ❌ 需同步机制 |

> **结论**：V1 采用 **MCP 实时读取方案**（与 Claude/Codex/Cursor 一致）。Wiki KB 作为 **V2 可选能力**，仅用于用户明确指定的"静态知识库"场景（如公司制度、产品手册等不常变的文档）。

---

#### 场景 3：文件夹右键 → AI 助手分析（按需读取，不建 KB）

```
┌──────────────────────────────────────────────────────────────┐
│  云盘 Web UI                                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  📁 项目文档/                                           │ │
│  │       ┌──────────────────┐                              │ │
│  │       │  打开             │                             │ │
│  │       │  下载             │                             │ │
│  │       │  分享             │                             │ │
│  │       │  🤖 AI助手分析 →  │                              │ │
│  │       └──────────────────┘                              │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  点击 "AI助手分析" →                                         │
│    1. 调 POST /api/v1/ai/ticket (附加 folderId=678)          │
│    2. 新窗口打开:                                            │
│       https://ai.example.com/?ticket=xxx&folderId=678        │
│       &folderName=项目文档                                    │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  MateClaw（新窗口）— Agent 实时分析模式                       │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  🤖 AI 助手                              [文件夹: 项目文档] │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 📁 正在浏览文件夹"项目文档"...                    │    │ │
│  │  │ 发现 27 个文件:                                  │    │ │
│  │  │                                                  │    │ │
│  │  │ 📄 需求文档/PRD-v2.md (12KB)                     │    │ │
│  │  │ 📄 设计文档/API设计.docx (45KB)                  │    │ │
│  │  │ 📄 设计文档/数据库设计.md (18KB)                  │    │ │
│  │  │ 📄 会议纪要/Q3复盘.md (8KB)                      │    │ │
│  │  │ ... 等 27 个文件                                 │    │ │
│  │  │                                                  │    │ │
│  │  │ 我已经看到了项目文档的结构。你可以问我:             │    │ │
│  │  │ • "总结PRD的核心需求"                             │    │ │
│  │  │ • "API设计中有哪些接口?"                          │    │ │
│  │  │ • "数据库表结构是怎样的?"                          │    │ │
│  │  │ • "对比PRD和Q3复盘中的差异"                       │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 💬 "总结PRD的核心需求"                     [发送] │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  处理流程（ReAct 模式）：                                     │
│    1. Agent 调 MCP file.list(folderId, recursive=true)      │
│       → 获取 27 个文件的列表（名称、大小、类型）               │
│                                                              │
│    2. Agent 将文件列表作为上下文，理解文件夹结构               │
│       → 发现关键文件: PRD, API设计, 数据库设计...             │
│                                                              │
│    3. 用户提问 → Agent 思考 → 选择相关文件                    │
│       → "总结PRD" → Agent 调 file.read(PRD.md的id)          │
│       → "API接口" → Agent 调 file.read(API设计的id)          │
│       → "对比..." → Agent 调 file.read(多个相关文件)          │
│                                                              │
│    4. 始终实时读取，不建 KB，不存快照                          │
│       → 用户改了文件再问，Agent 重新 read，读到最新版本         │
│                                                              │
│    5. Agent 有上下文窗口限制，大文件夹会选择性读取              │
│       → 先用 file.list 理解全貌                              │
│       → 对关键文件做 file.read(预览)确认内容                  │
│       → 相关文件全量读取，不相关文件跳过                       │
└──────────────────────────────────────────────────────────────┘
```

**对比旧方案（KB导入）的关键优势**：

| 维度 | 旧方案：导入KB | 新方案：ReAct 实时读取 |
|---|---|---|
| **文件夹文件变更后** | ❌ KB 过时，需重建/同步 | ✅ 下次 `file.read` 自动读到最新 |
| **用户新增文件** | ❌ KB 不会自动包含 | ✅ 下次 `file.list` 自动可见 |
| **用户删除文件** | ❌ KB 仍包含已删除内容 | ✅ 下次 `file.list` 自动消失 |
| **27个文件的总内容 50MB** | ❌ 全部导入，慢 | ✅ 按需读取相关文件，快 |
| **维护成本** | ❌ 需要同步机制 | ✅ 零维护 |
| **与 Claude/Cursor 一致** | ❌ | ✅ |

**MateClaw 端实现**（极简，无需 KB API）：

```typescript
async function handleFolderContext(folderId: number, folderName: string) {
  // 1. 获取文件夹结构作为 Agent 初始上下文
  const fileList = await mcpClient.callTool('file.list', {
    parentId: folderId,
    recursive: true,
    maxResults: 200,  // Agent 一次性感知的文件数上限
  });

  // 2. 构造 Agent 的 system prompt
  const contextMessage = `用户打开了云盘文件夹"${folderName}"。
该文件夹包含 ${fileList.total} 个文件。以下是文件列表:

${fileList.items.map(f => `- ${f.name} (${f.type}, ${formatSize(f.size)}, ${f.mimeType})`).join('\n')}

当用户提问时：
- 先用 file.list 确认文件夹结构（如果文件很多）
- 再用 file.read 读取相关文件的内容
- 始终实时读取，确保内容是最新的
- 如果文件列表超过50个，优先根据文件名判断相关性再读取`;

  // 3. 创建会话，注入文件夹上下文
  // Agent 在后续对话中会自己调用 file.read 按需读取
  await chatService.createSession({
    folderContext: { folderId, folderName, fileList },
    systemContext: contextMessage,
  });
}
```

> **V2 可选增强**：对于用户明确标记为"知识库"的文件夹（如公司制度、产品手册），提供"一键导入 Wiki KB"功能。此时需要处理同步问题（手动触发重新导入、或定时自动同步）。

---

---

#### 场景 4：会话中 @ 选择云盘文件

```
┌──────────────────────────────────────────────────────────────┐
│  MateClaw 会话页                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  🤖 AI 助手                                             │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 用户: 帮我分析一下这几个文件                      │    │ │
│  │  │ 🤖: 好的，请 @ 选择需要分析的文件                  │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 💬 @合                                  [发送]   │    │ │
│  │  │     ┌──────────────────────────────┐            │    │ │
│  │  │     │ 🔍 搜索云盘文件...            │            │    │ │
│  │  │     │ ──────────────────────────── │            │    │ │
│  │  │     │ 📄 合同文件/                │  ← MCP      │    │ │
│  │  │     │   └ Q3销售合同-v3.docx       │  file.list  │    │ │
│  │  │     │   └ 采购合同2026.pdf         │  提供列表    │    │ │
│  │  │     │ 📄 报告/                    │            │    │ │
│  │  │     │   └ 季度分析报告.xlsx        │            │    │ │
│  │  │     │ 📄 项目文档/README.md        │            │    │ │
│  │  │     └──────────────────────────────┘            │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  处理流程:                                                   │
│    1. 用户输入 @ → 触发 CloudFilePicker 组件                  │
│    2. 组件调 MCP file.search / file.list 获取文件列表         │
│    3. 用户搜索/浏览 → 选择一个或多个文件                      │
│    4. 选中文件作为附件关联到当前消息                           │
│    5. 发送消息时，Agent 调 MCP file.read 获取每个文件内容      │
│    6. 文件内容注入会话上下文 → AI 基于文件回答                 │
└──────────────────────────────────────────────────────────────┘
```

**MateClaw 端实现**：新增 `CloudFilePicker.vue` 组件

```typescript
// 新组件: src/components/chat/CloudFilePicker.vue
// 触发方式: 用户在输入框输入 @ 时弹出
// 数据来源: MCP file.search / file.list

interface CloudFilePickerProps {
  visible: boolean;
  onSelect: (files: CloudFileItem[]) => void;
}

// 搜索逻辑
async function searchFiles(query: string) {
  // 通过 MateClaw Agent 的 MCP Client 调用 file.search
  const result = await agentMcpClient.callTool('file.search', {
    query,
    maxResults: 20,
  });
  return result.files;
}

// 选中文件后:
// 1. 文件作为附件显示在输入框下方
// 2. 实际文件内容在 Agent 处理消息时通过 file.read 懒加载
```

---

#### 场景 5：AI 生成文件 → 保存到云盘

```
┌──────────────────────────────────────────────────────────────┐
│  MateClaw 会话页                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  用户: 帮我写一份Q3销售总结报告，Markdown格式             │ │
│  │  🤖: 好的，已经生成报告：                                │ │
│  │       ┌──────────────────────────────────────────┐      │ │
│  │       │ # Q3销售总结报告                         │      │ │
│  │       │                                          │      │ │
│  │       │ ## 一、总体情况                          │      │ │
│  │       │ Q3实现销售收入...                        │      │ │
│  │       │ ...                                      │      │ │
│  │       │                                          │      │ │
│  │       │ 📁 保存到云盘 →                          │      │ │
│  │       └──────────────────────────────────────────┘      │ │
│  │                                                         │ │
│  │  用户点击 "保存到云盘" →                                  │ │
│  │       ┌──────────────────────────────────────────┐      │ │
│  │       │ 保存到云盘                                │      │ │
│  │       │                                          │      │ │
│  │       │ 目标位置: [项目文档/Reports ▼]  [浏览..]  │      │ │
│  │       │ 文件名:   [Q3销售总结报告.md           ]  │      │ │
│  │       │                                          │      │ │
│  │       │ [取消]              [确认保存]            │      │ │
│  │       └──────────────────────────────────────────┘      │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  处理流程:                                                   │
│    1. AI 生成内容并展示                                      │
│    2. Agent 提供 "保存到云盘" 按钮（Tool Call 确认）          │
│    3. 用户点击 → 弹出保存对话框                               │
│    4. 对话框调 MCP file.list 展示目标位置目录结构             │
│    5. 用户选择/输入目标路径 + 文件名                          │
│    6. 确认 → Agent 调 MCP file.create 保存文件               │
│    7. 返回成功 + 云盘文件链接                                 │
└──────────────────────────────────────────────────────────────┘
```

**MateClaw 端实现**：通过 Agent Tool Call 的确认机制

```typescript
// Agent 在生成内容后，自动调用 file.create tool
// MateClaw 的 Tool 确认机制会弹出对话框让用户确认

// Tool Call 参数（Agent 自动填充）:
{
  tool: "file.create",
  params: {
    parentId: null,        // 需要用户选择
    name: "Q3销售总结报告.md",
    content: "# Q3销售总结报告\n\n...",
    mimeType: "text/markdown",
  }
}

// 确认对话框让用户:
// 1. 浏览选择目标文件夹（通过 MCP file.list）
// 2. 确认/修改文件名
// 3. 确认保存
```

---

### 12.4 上下文窗口与记忆管理分析

> **核心问题**：ReAct 模式每次 `file.read` 都把文件内容装入上下文窗口。大文件、多文件、长对话会不会撑爆窗口？

#### Token 预算算一算

**2026 年主流模型上下文窗口**：

| 模型 | 上下文窗口 | 约等于 |
|---|---|---|
| Claude Opus 4 / Sonnet 4 | 200K tokens | ~15 万英文词 / ~30 万中文字 |
| GPT-4o | 128K tokens | ~10 万英文词 |
| DeepSeek V3 | 128K tokens | ~10 万英文词 |
| Gemini 2.5 Pro | 1M tokens（有效 ~200K） | ~15 万英文词 |

**典型场景的 Token 消耗**：

| 场景 | Token 消耗 | 占 200K 窗口 |
|---|---|---|
| System prompt + MCP 工具定义 | ~2K | 1% |
| 文件夹列表（200 个文件） | ~3K | 1.5% |
| 读 1 个 Markdown 文件（12KB） | ~3K | 1.5% |
| 读 1 个 Word 文档（45KB） | ~12K | 6% |
| 读 1 个 PDF（100 页） | ~25K | 12.5% |
| 读 5 个相关文件（选中） | ~20-50K | 10-25% |
| 读全部 27 个文件（全量） | ~60K | 30% |
| 10 轮对话历史 | ~5K | 2.5% |
| **典型会话总计**（读 5 文件 + 对话） | **~30-60K** | **15-30%** ✅ |
| **最坏情况**（全量读取 + 长对话） | **~100K** | **50%** ✅ |

> **结论**：200K 窗口足够应对绝大多数场景。典型使用（读 3-5 个相关文件）只占总窗口的 15-30%。

#### 三个潜在风险与缓解

##### 风险 1：单个超大文件（100MB 的日志文件）

```
❌ 问题: file.read 返回 100MB 文本 → 瞬间撑爆窗口
✅ 缓解:
  - file.read 默认 maxChars=50000 (~12K tokens)，用户可手动增大
  - 大文件分段读取: file.read(fileId, offset=0, limit=10000)
  - 新增 file.grep 工具: 在大文件中搜索关键词，只返回匹配行+上下文
```

##### 风险 2：长对话中反复读不同文件

```
❌ 问题: 用户连续问 10 个不同文件 → 上下文累计 ~100K+ tokens → 早期对话被截断
✅ 缓解:
  - Agent 自动总结策略:
    "我已读取了以下文件: PRD.md(核心需求是...), API设计.docx(接口有...)"
    用 200 tokens 替代 20K tokens 的原文
  - 用户手动 "清空上下文" 或 "只看这三个文件"
  - 新一代模型自动上下文压缩（Claude/GPT 均支持）
```

##### 风险 3：Agent 过度读取

```
❌ 问题: Agent 紧张，把 200 个文件全读了一遍 → 窗口爆了
✅ 缓解:
  - System prompt 约束:
    "读取文件前，先根据文件名和用户问题判断相关性。
     优先读文件名匹配度最高的 3-5 个文件。
     如果信息不足，再逐步扩大读取范围。"
  - 新增 file.summarize 工具:
    不读全文，返回文件的结构化摘要（标题、关键词、前 200 字）
    帮助 Agent 判断是否值得全量读取
```

#### 新增工具：`file.grep` + `file.summarize`

为应对上下文窗口挑战，MCP Server 新增 2 个辅助工具：

```yaml
# file.grep — 在文件内容中搜索关键词（不加载全文）
name: file.grep
description: >
  在指定文件中搜索关键词，返回匹配行及上下文。适合在大文件中精确定位信息，
  不消耗大量上下文。类似 grep 命令。
parameters:
  fileId: number       # 文件ID
  pattern: string      # 搜索关键词（支持简单正则）
  contextLines: number # 上下文行数，默认 3
  maxMatches: number   # 最大匹配数，默认 20
returns:
  matches: [{line, lineNumber, context}]
  totalMatches: number
  truncated: boolean   # 匹配数是否被截断

# file.summarize — 获取文件结构摘要（不含全文）
name: file.summarize
description: >
  快速获取文件的结构化摘要：标题、关键词、前 200 字符预览、目录结构。
  用于 Agent 判断文件是否值得完整读取，节省上下文。
parameters:
  fileId: number
returns:
  title: string
  keywords: [string]
  preview: string        # 前 200 字符
  headings: [string]     # 文档标题结构
  pageCount: number
  size: number
```

#### 记忆策略：V1 短期记忆，V2 长期记忆

```
V1 (本期): 会话级短期记忆
  ┌─────────────────────────────────────┐
  │  对话上下文 (200K 窗口)              │
  │  ┌─────────────────────────────────┐│
  │  │ System Prompt                   ││
  │  │ 文件读取结果 (按需，不持久化)     ││
  │  │ 对话历史                         ││
  │  │ Agent 自动摘要 (压缩旧内容)       ││
  │  └─────────────────────────────────┘│
  │  会话结束 → 上下文释放               │
  └─────────────────────────────────────┘
  
  足够应对: 单次会话中分析 3-10 个文件

V2 (远期): 跨会话长期记忆
  ┌─────────────────────────────────────┐
  │  MateClaw Memory 系统               │
  │  • 用户偏好记忆（偏好文件类型等）     │
  │  • 关键结论持久化（"上次分析结论"）   │
  │  • Wiki KB（用户标记的静态知识库）    │
  └─────────────────────────────────────┘
  
  不是 V1 必须的
```

---

### 12.5 各端 V1 开发任务汇总（精简版）

#### 云盘前端（yly-saas-web-cloud-driver）

| # | 任务 | 说明 | 工时 |
|---|---|---|---|
| F1 | 主导航加 "AI助手" 按钮 | 右上角导航栏，图标+文字，点击触发 ticket 跳转 | 0.5天 |
| F2 | 文件右键菜单加 "AI助手分析" | 单文件右键菜单增加菜单项，传 fileId | 0.5天 |
| F3 | 文件夹右键菜单加 "AI助手分析" | 文件夹右键菜单增加菜单项，传 folderId+folderName | 0.5天 |
| F4 | 调用 ticket 生成 API | `POST /api/v1/ai/ticket`，获取 ticket JWT 后打开新窗口 | 0.5天 |

#### 云盘后端（yly-saas-cdms-ai）

| # | 任务 | 说明 | 工时 |
|---|---|---|---|
| B1 | 新增 `POST /api/v1/ai/ticket` | HMAC-SHA256 签名生成 JWT ticket，5分钟有效期，Redis 防重放 | 0.5天 |
| B2 | 共享密钥配置 | `application.yml` 中配置 `yliyun.ai.ticket-secret` | — |

```java
// B1: TicketController.java (新增)
@PostMapping("/api/v1/ai/ticket")
@Operation(summary = "生成 AI 助手免登录 ticket")
public CommonResult<String> generateAITicket() {
    Long userId = getLoginUserId();
    Long tenantId = TenantContextHolder.getTenantId();
    AdminUserDO user = userService.getUser(userId);
    
    String ticket = JWT.create()
        .withSubject("yliyun-ticket")
        .withClaim("userId", userId)
        .withClaim("tenantId", tenantId)
        .withClaim("nickname", user.getNickname())
        .withJWTId(IdUtil.fastSimpleUUID())
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + 300_000)) // 5min
        .sign(Algorithm.HMAC256(ticketSecret));
    
    // 记录 jti 到 Redis，5分钟过期，防止重放
    redisTemplate.opsForValue().set("ai:ticket:" + ticketId, "1", 5, TimeUnit.MINUTES);
    
    return success(ticket);
}
```

#### MateClaw 后端（mateclaw-server）

| # | 任务 | 说明 | 工时 |
|---|---|---|---|
| M1 | Ticket 登录端点 | `GET /api/v1/auth/yliyun/ticket?ticket=&redirect=` | 1天 |
| M2 | 用户映射表扩展 | `mc_workspace_user` 加 `yliyun_user_id`, `yliyun_tenant_id` | 0.5天 |
| M3 | MCP Client 用户身份透传 | MateClaw → MCP Server 时自动带 `X-Forward-User-Id` 等头部 | 已在 Phase 3C 中 |

```java
// M1: YliyunAuthController.java (新增)
@GetMapping("/api/v1/auth/yliyun/ticket")
@PermitAll
public void handleTicket(@RequestParam("ticket") String ticket,
                         @RequestParam(value = "redirect", defaultValue = "/chat") String redirect,
                         HttpServletResponse response) throws Exception {
    // 1. 验签
    DecodedJWT jwt = JWT.require(Algorithm.HMAC256(yliyunTicketSecret))
        .withSubject("yliyun-ticket")
        .build().verify(ticket);
    
    // 2. 防重放
    String jti = jwt.getId();
    if (!redisTemplate.opsForValue().setIfAbsent("mate:ticket:" + jti, "1", 5, TimeUnit.MINUTES)) {
        throw new BadRequestException("ticket 已被使用");
    }
    
    // 3. 解析用户信息
    Long yliyunUserId = jwt.getClaim("userId").asLong();
    Long yliyunTenantId = jwt.getClaim("tenantId").asLong();
    String nickname = jwt.getClaim("nickname").asString();
    
    // 4. 查找或创建映射用户
    McWorkspaceUser user = yliyunUserMappingService.findOrCreateUser(
        yliyunUserId, yliyunTenantId, nickname);
    
    // 5. 签发 MateClaw JWT
    String mateJwt = tokenService.createToken(user);
    
    // 6. 重定向
    response.sendRedirect(redirect + "#token=" + mateJwt);
}
```

#### MateClaw 前端（mateclaw-ui）

| # | 任务 | 说明 | 工时 |
|---|---|---|---|
| U1 | 处理 ticket URL 参数 | `?ticket=xxx` → 调 `/api/v1/auth/yliyun/ticket` → 获取 token → 自动登录 | 0.5天 |
| U2 | 处理 fileId/folderId 参数 | 进入会话后自动调 MCP 加载文件/文件夹上下文（文件夹仅 list 不建 KB） | 0.5天 |
| U3 | `CloudFilePicker.vue` | @ 触发的云盘文件选择器，通过 MCP 获取列表 | 1.5天 |
| U4 | 保存到云盘确认对话框 | Agent Tool Call 确认时展示保存位置选择 UI | 1天 |

#### MCP Server（yliyun-mcp-server）

| # | 任务 | 说明 | 工时 |
|---|---|---|---|
| P1 | 14 个 MCP Tool 全实现 | 12 核心 + 2 辅助（`file.grep`、`file.summarize`），已在 Phase 3A 中规划 | 3-5天 |
| P2 | MateClaw 内部服务认证 | 支持 `X-Forward-User-Id` / `X-Forward-Tenant-Id` 头部透传 | 已包含 |
| P3 | 文本提取优化 | 集成 pdf-parse、mammoth（docx）等专用库提升提取质量 | 1天 |

---

### 12.6 V1 完整测试流程与验证方案

#### 测试环境准备

```bash
# 1. 启动云盘
cd /Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai
# 按云盘项目文档启动

# 2. 启动 MCP Server
cd /Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server
cp .env.example .env
# 编辑 .env: YLIYUN_API_BASE_URL=http://localhost:8080
pnpm install && pnpm dev

# 3. 启动 MateClaw
cd /Users/qinjinlong/Documents/projects/ai/yliyunclaw
docker-compose up -d

# 4. 配置 MateClaw → MCP Server
# MateClaw Admin → MCP Server 管理 → 添加 yliyun-mcp
```

#### 测试用例清单

##### TC-0: 免登录认证链路（最高优先级）

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-0.1 | 云盘登录 → 点击 "AI助手" | 新窗口打开 MateClaw，自动登录 | 无登录页闪现 |
| TC-0.2 | 检查 MateClaw 用户 | 自动创建映射用户 | `mc_workspace_user.yliyun_user_id` 有值 |
| TC-0.3 | 重复点击 "AI助手" | 同一用户，不重复创建 | ticket 防重放生效 |
| TC-0.4 | 使用过期 ticket | 显示错误 "ticket 已过期" | 5分钟过期验证 |
| TC-0.5 | 使用伪造 ticket | 显示错误 "ticket 无效" | 签名验证 |

##### TC-1: 主导航 → AI 助手

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-1.1 | 云盘主导航点击 "AI助手" | MateClaw 新窗口打开，已登录 | — |
| TC-1.2 | 在 MateClaw 中输入 "帮我找合同文件" | Agent 调 MCP `file.search(query="合同")` | MCP Tool 被调用 |
| TC-1.3 | 查看返回结果 | 显示云盘中的合同文件列表 | 文件名、大小、日期正确 |
| TC-1.4 | 点击某个文件 | Agent 调 MCP `file.read(fileId)` | 文件内容正确展示 |

##### TC-2: 文件右键 → AI 分析

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-2.1 | 云盘中右键 .docx 文件 → "AI助手分析" | MateClaw 新窗口打开，自动加载文件 | — |
| TC-2.2 | 检查初始消息 | 显示 "已加载文件: xxx.docx" | 文件名正确 |
| TC-2.3 | 问 "总结这份文档" | AI 返回文档内容的摘要 | 基于实际文件内容回答 |
| TC-2.4 | 右键 .xlsx 文件 → "AI助手分析" | AI 可识别表格结构 | Excel 数据正确解析 |
| TC-2.5 | 右键 .pdf 文件 → "AI助手分析" | AI 可提取 PDF 文本 | PDF 文本正确提取 |
| TC-2.6 | 右键已删除文件 → "AI助手分析" | 显示 "文件不存在或已删除" | 错误提示友好 |

##### TC-3: 文件夹右键 → AI 分析（ReAct 实时读取）

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-3.1 | 云盘中右键文件夹(含10个文件) → "AI助手分析" | MateClaw 新窗口打开，Agent 自动调 `file.list`，列出文件 | 文件夹结构正确展示 |
| TC-3.2 | 问 "总结PRD文档的核心需求" | Agent 调 `file.read(PRD文件)` → 基于最新内容回答 | 实时读取，始终最新 |
| TC-3.3 | 云盘中修改 PRD 文件内容后，再问 "总结PRD" | Agent 重新 `file.read`，读到修改后的最新内容 | **验证：不建KB，自动同步** |
| TC-3.4 | 云盘中新增文件到该文件夹，问 "文件夹里有哪些文件" | Agent 重新 `file.list`，新文件出现在列表中 | **验证：新增文件自动可见** |
| TC-3.5 | 云盘中删除文件，问 "文件夹概况" | Agent 重新 `file.list`，已删除文件不再出现 | **验证：删除文件自动消失** |
| TC-3.6 | 问跨文件问题 "对比PRD和技术方案" | Agent 先后调 `file.read(PRD)` + `file.read(技术方案)` → 综合回答 | 多文件关联推理 |
| TC-3.7 | 右键空文件夹 → "AI助手分析" | Agent 调 `file.list` → 提示 "文件夹为空" | 友好提示 |
| TC-3.8 | 文件夹有 200 个文件，问 "哪些是合同相关" | Agent 先 `file.list`，根据文件名推断相关性，只读匹配的文件 | **验证：选择性读取，不读无关文件** |

##### TC-4: 会话中 @ 选择云盘文件

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-4.1 | 在 MateClaw 输入框输入 @ | 弹出云盘文件选择器 | — |
| TC-4.2 | 在文件选择器中搜索 "报告" | 显示匹配的云盘文件列表 | 通过 MCP file.search |
| TC-4.3 | 浏览目录 | 可展开文件夹 | 通过 MCP file.list |
| TC-4.4 | 选择一个文件 | 文件名出现在输入框附件区 | — |
| TC-4.5 | 发送消息 "总结这个文件" | AI 读取选中的文件并总结 | 文件内容正确注入 |
| TC-4.6 | 选择 3 个文件后发送 | AI 分别读取 3 个文件并对比 | 多文件处理 |
| TC-4.7 | 输入 @ 后按 Escape | 文件选择器关闭 | — |

##### TC-5: AI 生成文件 → 保存到云盘

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-5.1 | 在 MateClaw 中说 "写一份项目计划书" | AI 生成内容，显示 "保存到云盘" 按钮 | — |
| TC-5.2 | 点击 "保存到云盘" | 弹出保存对话框，显示云盘目录 | 通过 MCP file.list |
| TC-5.3 | 选择目标文件夹，确认文件名，保存 | 文件成功保存到云盘 | MCP file.create 成功 |
| TC-5.4 | 回到云盘验证文件 | 文件存在，内容正确 | 文件内容一致 |
| TC-5.5 | 保存到不存在的路径 | 提示错误 | 错误处理 |
| TC-5.6 | 保存时磁盘空间不足 | 提示 "存储空间不足" | QUOTA_EXCEEDED 错误处理 |

##### TC-6: 权限与安全

| 步骤 | 操作 | 预期结果 | 验证点 |
|---|---|---|---|
| TC-6.1 | 用户A尝试读取用户B的私有文件 | 返回 "无权限" | 租户隔离 |
| TC-6.2 | 用户A尝试删除用户B的文件 | 返回 "无权限" | 权限校验 |
| TC-6.3 | 短时间内大量请求 | 触发限流，返回 RATE_LIMITED | 速率限制生效 |

##### TC-7: 端到端完整流程（冒烟测试）

| 步骤 | 操作 | 预期结果 |
|---|---|---|
| 1 | 云盘登录 → 点击 "AI助手" → MateClaw 自动登录 | ✅ |
| 2 | @ 搜索 "Q3销售报告.xlsx" → 选择文件 → "分析Q3销售趋势" | AI 正确分析 |
| 3 | "帮我把分析结果写成 Markdown 报告" | AI 生成报告 |
| 4 | 保存报告到 "项目文档/Reports/" 目录 | 文件保存成功 |
| 5 | 回到云盘，右键 Reports 文件夹 → "AI助手分析" | Agent 列出文件，等待用户提问 |
| 6 | "对比一下这个季度和上个季度的报告差异" | Agent 读取两个报告 → 跨文件对比 |

---

### 12.7 V1 数据流总览

```
云盘后端                    MCP Server                  MateClaw
─────────                  ──────────                  ────────
                           
POST /api/v1/ai/ticket     
  → HMAC签名JWT ──────────────────────────────→ GET /api/v1/auth/yliyun/ticket
  (userId,tenantId,                            → 验签+用户映射+签发JWT
   nickname,exp=5min)                          
                                                  
                                              用户进入会话
                                                │
                                                │ 用户 @ 选文件
                                                │ → MCP file.search ──→ GET /cloud-drive/file/list
                                                │ ← 文件列表          ← 返回文件列表
                                                │
                                                │ 用户选文件发消息
                                                │ → MCP file.read ────→ GET /cloud-drive/file/download
                                                │ ← 文本内容          ← 返回文件流
                                                │                      (MCP提取文本)
                                                │
                                                │ AI 生成内容
                                                │ 用户确认保存
                                                │ → MCP file.create ──→ POST /cloud-drive/file/upload
                                                │ ← 保存成功          ← 返回文件信息
                                                
                          云盘已有API (zero-change)
                          ────────────────────────
                          /extends/user-token/get  (Token生成)
                          /system/oauth2/check-token (Token校验)
                          /cloud-drive/file/list   (文件列表)
                          /cloud-drive/file/download (下载)
                          /cloud-drive/file/upload  (上传)
                          /cloud-drive/file/delete  (删除)
                          /cloud-drive/file/move    (移动)
                          /cloud-drive/share-link/create (外链)
                          /cloud-drive/file-tag/rel/save (标签)
                          /cloud-drive/file-version/list (版本)
                          /cloud-drive/space/dept-list   (空间)
```

---

## 十三、MCP Server 项目初始化

### 13.1 项目创建

项目路径：`/Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server/`

```bash
mkdir -p /Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server
cd /Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server
pnpm init
```

### 13.2 项目结构与初始化文件

完整的项目结构（已根据实际云盘 API 调整）：

```
yliyun-mcp-server/
├── package.json
├── tsconfig.json
├── vitest.config.ts
├── .env                          # 开发环境变量
├── .env.example                  # 环境变量模板
├── Dockerfile
├── docker-compose.dev.yml        # 开发环境（含云盘 mock）
├── README.md
├── src/
│   ├── index.ts                  # 服务入口
│   ├── server.ts                 # FastMCP Server 配置
│   ├── config.ts                 # 环境变量 + 云盘 API 配置
│   ├── auth/
│   │   ├── index.ts              # 认证中间件
│   │   ├── ticket.ts             # Ticket 兑票逻辑 → POST /extends/user-token/get
│   │   └── apikey.ts             # API Key 验证
│   ├── cloud-api/
│   │   ├── client.ts             # 云盘 HTTP Client（连接池、重试、熔断）
│   │   ├── file.ts               # 文件 API 封装（/cloud-drive/file/*）
│   │   ├── version.ts            # 版本 API 封装（/cloud-drive/file-version/*）
│   │   ├── share.ts              # 分享 API 封装（/cloud-drive/share-link/*）
│   │   ├── tag.ts                # 标签 API 封装（/cloud-drive/file-tag/*）
│   │   ├── space.ts              # 空间 API 封装（/cloud-drive/space/*）
│   │   └── auth.ts               # OAuth2 Token 校验（/system/oauth2/check-token）
│   ├── tools/
│   │   ├── index.ts              # 工具注册中心
│   │   ├── file-search.ts        # file.search
│   │   ├── file-read.ts          # file.read
│   │   ├── file-list.ts          # file.list
│   │   ├── file-create.ts        # file.create
│   │   ├── file-save.ts          # file.save
│   │   ├── file-move.ts          # file.move
│   │   ├── file-delete.ts        # file.delete
│   │   ├── file-tag.ts           # file.tag
│   │   ├── file-share.ts         # file.share_link
│   │   ├── file-versions.ts      # file.versions
│   │   ├── space-context.ts      # space.context
│   │   └── user-profile.ts       # user.profile
│   ├── schemas/
│   │   └── index.ts              # Zod Schema 定义（参数 + 返回值）
│   ├── errors.ts                 # 结构化错误定义
│   └── middleware/
│       ├── rate-limiter.ts       # Token Bucket 速率限制
│       ├── idempotency.ts        # 写操作幂等控制
│       └── logger.ts             # 审计日志
├── tests/
│   ├── unit/                     # 单元测试（每个 Tool）
│   ├── integration/              # MCP Inspector 集成测试
│   └── cross-client/             # 多客户端兼容性测试
└── scripts/
    ├── dev.sh                    # 开发环境一键启动
    └── test-mcp-inspector.sh     # MCP Inspector 测试
```

### 13.3 关键实现文件

#### `package.json`

```json
{
  "name": "yliyun-mcp-server",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "tsx --watch src/index.ts",
    "build": "tsc",
    "start": "node dist/index.js",
    "test": "vitest",
    "test:integration": "vitest --config vitest.integration.config.ts",
    "lint": "eslint src/",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "fastmcp": "^3.x",
    "zod": "^3.x",
    "undici": "^6.x"
  },
  "devDependencies": {
    "typescript": "^5.x",
    "tsx": "^4.x",
    "vitest": "^2.x",
    "@types/node": "^22.x",
    "eslint": "^9.x"
  }
}
```

#### `.env` 环境变量

```bash
# MCP Server
MCP_PORT=18100
MCP_HOST=0.0.0.0
MCP_TRANSPORT=streamable-http   # streamable-http | sse

# 云盘 API
YLIYUN_API_BASE_URL=http://localhost:8080
YLIYUN_TOKEN_ENDPOINT=/extends/user-token/get
YLIYUN_CHECK_TOKEN_ENDPOINT=/system/oauth2/check-token

# 白名单认证（MCP Server → 云盘）
YLIYUN_APP_KEY=mcp-server-internal
YLIYUN_DEFAULT_TENANT_ID=1

# API Key（外部 AI 客户端 → MCP Server）
MCP_API_KEYS=key_claude_xxx,key_codex_xxx,key_cursor_xxx

# 速率限制
RATE_LIMIT_MAX_REQUESTS=100
RATE_LIMIT_WINDOW_SECONDS=60
```

#### `src/server.ts` — FastMCP Server 核心配置

```typescript
import { FastMCP } from 'fastmcp';
import { registerAllTools } from './tools/index.js';
import { authenticateRequest } from './auth/index.js';
import { config } from './config.js';

export function createServer() {
  const server = new FastMCP({
    name: 'yliyun-mcp-server',
    version: '1.0.0',
    description: '一粒云云盘 MCP Server — 提供文件搜索、读写、分享、标签等云盘能力的标准化 MCP 接口',
    
    // 认证：每个请求到达时校验 Authorization header
    authenticate: async (req) => {
      return await authenticateRequest(req);
    },
    
    // Streamable HTTP 配置
    transport: 'streamable-http',
    port: config.port,
    host: config.host,
  });

  // 注册所有 MCP Tools
  registerAllTools(server);

  return server;
}
```

#### `src/auth/ticket.ts` — Ticket 兑票逻辑

```typescript
// 核心逻辑：将云盘 ticket 兑换为 OAuth2 token
// POST /extends/user-token/get
// 请求: { tenantId, userId/appKey }
// 响应: { token, userId, nickname, expiresTime }

export async function exchangeTicket(ticket: string): Promise<UserContext> {
  const resp = await cloudAPI.post(config.tokenEndpoint, {
    tenantId: config.defaultTenantId,
    userId: extractUserIdFromTicket(ticket),
    appKey: config.appKey,
  }, {
    headers: { 'Authorization': `Bearer ${ticket}` }
  });
  
  // 同时校验 token 有效性
  await validateToken(resp.token);
  
  return {
    userId: resp.userId,
    tenantId: config.defaultTenantId,
    nickname: resp.nickname,
    accessToken: resp.token,
    expiresAt: resp.expiresTime,
  };
}
```

#### MCP Tool 实现示例：`file.read`

```typescript
// src/tools/file-read.ts
// 对应的云盘 API：优先使用 /cloud-drive/file/text-content（P0 新增）
// 降级方案：/cloud-drive/file/preview（元数据）+ /cloud-drive/file/content（需要 previewToken）

server.addTool({
  name: 'file.read',
  description: `读取云盘文件的内容。支持文本/文档/PDF/图片（OCR）。
    自动提取文本内容并返回 Markdown 格式。
    大文件默认截断到 50000 字符。
    使用场景：用户说"帮我看看这份合同"、"总结这份 PDF"`,
  parameters: z.object({
    fileId: z.number().int().positive().describe('文件ID，可从 file.search 或 file.list 获取'),
    maxChars: z.number().int().min(100).max(200000).default(50000)
      .describe('最大返回字符数，超出截断并标记'),
    format: z.enum(['text', 'markdown']).default('markdown')
      .describe('返回格式'),
  }),
  execute: async (args, { session }) => {
    // Step 1: 调用云盘 text-content API 获取文本内容
    const result = await cloudAPI.file.getTextContent(
      args.fileId, args.maxChars, session.accessToken
    );
    
    // Step 2: 格式化返回
    return {
      content: args.format === 'markdown' ? result.content : stripMarkdown(result.content),
      fileName: result.fileName,
      mimeType: result.mimeType,
      charCount: result.charCount,
      truncated: result.truncated,
      metadata: result.metadata,
    };
  }
});
```

### 13.4 V1 业务打通 — 实施清单

> **目标**：claw → MCP → 云盘 整条链路可演示。用户能从 MateClaw 搜索/浏览/读取云盘文件，AI 处理后保存回云盘。
> **策略**：全用现有云盘 API，MCP Server 侧做文本提取。云盘侧 zero-change。
>
> **状态说明（2026-07-30）**：以下复选框是最初的历史拆分，不再作为实时状态源，其中还包含已废弃的固定透传头和旧工具数量。当前 V1/V2 的逐项状态、V3 计划与统一应用接入治理任务以 `handover-yliyun-integration.md` 第十二至十五章为准：V1 核心链可用但生产验收未收口，V2 和 IG 尚未完成。

#### Phase A: MCP Server 核心实现（3-5天）

- [ ] **A1. 项目依赖安装与启动**
  - `pnpm install` + `pnpm dev` 跑通
  - 配置 `.env` 指向云盘开发环境
  - 验证 `http://localhost:18100/health` 可访问

- [ ] **A2. 认证链路打通** ⭐ 最高优先级
  - 实现 `POST /extends/user-token/get` 调用（Ticket → Token）
  - 实现 `POST /system/oauth2/check-token` 调用（Token 校验）
  - 三种认证方式：API Key + OAuth2 Token + MateClaw 内部服务头
  - **验证**：用 curl 带 token 调 MCP endpoint，认证通过

- [ ] **A3. 云盘 HTTP Client 实现**
  - 封装现有 API 调用（list/preview/download/upload/delete/move/rename/share-link/create/file-tag/rel/save 等）
  - 连接池 + 重试（3次）+ 超时处理
  - 统一错误转换（云盘 error → MCPError）

- [ ] **A4. 只读 Tools 实现**
  - `file.list` → `GET /cloud-drive/file/list`
  - `file.search` → `GET /cloud-drive/file/list?keyword=&recursive=true`
  - `file.read` → `GET /cloud-drive/file/download` + 本地文本提取（集成 Tika/pdf-parse）
  - `file.versions` → `GET /cloud-drive/file-version/list`
  - `space.context` → `GET /cloud-drive/space/dept-list`
  - `user.profile` → 从认证上下文返回

- [ ] **A5. 写入 Tools 实现**
  - `file.create` → `POST /cloud-drive/file/folder` + `POST /cloud-drive/file/upload`（文本转 Buffer multipart 上传）
  - `file.save` → `POST /cloud-drive/file-version/upload`
  - `file.move` → `PUT /cloud-drive/file/move` + `PUT /cloud-drive/file/rename`
  - `file.delete` → `DELETE /cloud-drive/file/delete`
  - `file.tag` → `PUT /cloud-drive/file-tag/rel/save`
  - `file.share_link` → `POST /cloud-drive/share-link/create`

- [ ] **A6. 中间件**
  - 速率限制（Token Bucket）
  - 审计日志（结构化 JSON，参数脱敏）
  - 结构化错误（8 种错误码 + suggestion）

#### Phase B: MCP Server 自测（1-2天）

- [ ] **B1. MCP Inspector 测试**
  - `tools/list` 返回 12 个工具
  - 每个 Tool 手动调一次，验证参数 Schema 和返回值
  - 错误场景验证（文件不存在、权限不足、参数非法）

- [ ] **B2. 单元测试**
  - 每个 Tool 的单元测试（Mock 云盘 API）
  - 覆盖率 ≥ 80%

#### Phase C: MateClaw ↔ MCP 集成（1-2天）

- [ ] **C1. MateClaw MCP Server 配置**
  - 在 MateClaw Admin 中添加 yliyun-mcp server
  - 配置 `X-Forward-User-Id` / `X-Forward-Tenant-Id` 头部透传
  - 验证 MateClaw Agent 可见 `file.*` 工具

- [ ] **C2. MateClaw 端到端验证**
  - 用户登录云盘 → 打开 AI 助手
  - MateClaw 通过 Ticket 获取云盘 Token
  - Agent 搜索文件："帮我找一下合同文件"
  - Agent 读取文件："总结这份文档"
  - Agent 保存文件："把润色后内容保存回云盘"

#### Phase D: 外部 AI 客户端验证（1天）

- [ ] **D1. Claude Desktop 配置**
  - 配置 `claude_desktop_config.json`
  - 验证 `file.search` / `file.read` / `file.list` 可用

#### Phase E: 生产部署准备（1天）

- [ ] **E1. Docker 构建**
  - `docker build` + `docker-compose up`
  - 验证容器健康检查

- [ ] **E2. 文档完善**
  - README 更新（V1 功能说明 + 配置指南）
  - 云盘侧 zero-change 确认项全部打勾

---

### 13.5 V2 迭代计划（V1 稳定后启动）

| V2 里程碑 | 内容 | 工时 |
|---|---|---|
| V2-M1: 云盘 API 补全 | text-content / write-content / search / space-context / user-profile | 6.5天 |
| V2-M2: MCP Server 升级 | 替换 V1 降级方案为专用 API，删除本地文本提取逻辑 | 2天 |
| V2-M3: WenShu 集成 | `wenshu.analyze` 完整实现 | 2-3天 |
| V2-M4: 全客户端验收 | 6 客户端 × 13 场景兼容性矩阵全部通过 | 3天 |

---

## 十四、云盘侧已有但 MCP Server 可选利用的能力

以下云盘功能对 MCP Server 是「锦上添花」，可在 V2 版本纳入：

| 云盘功能 | API | MCP Tool 候选 |
|---|---|---|
| 收藏夹 | `POST/GET/DELETE /cloud-drive/file-favorite/*` | `file.favorite_add` / `file.favorite_list` |
| 回收站 | `GET/POST/DELETE /cloud-drive/recycle/*` | `file.recycle_list` / `file.recycle_restore` |
| 最近访问 | `GET /cloud-drive/recent-file/page` | `file.recent` |
| 文件动态 | `GET /cloud-drive/file-dynamic/page` | `file.activity` |
| 文件备注 | `GET/POST /cloud-drive/file-remark/*` | `file.remark` |
| 文件权限 | `GET /cloud-drive/file-permission/*` | `file.permission_list`（管理员） |
| NAS 搜索 | `POST /nas/task/file_search_start` | （如果启用了 NAS 模块） |
| 桌面同步 | `POST /cloud-drive/sync/*` | （桌面客户端专用） |

---

## 附录：快速启动指南

### 1. 开发环境一键启动

```bash
# 1. 启动云盘开发环境（已有）
cd /Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai
# 按云盘项目文档启动后端 + 前端

# 2. 启动 MCP Server 开发环境
cd /Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server
cp .env.example .env
# 编辑 .env 填入实际云盘地址
pnpm install
pnpm dev

# 3. 验证 MCP Server 是否正常
# 使用 MCP Inspector
npx @modelcontextprotocol/inspector \
  --transport streamable-http \
  --url http://localhost:18100/mcp \
  --headers '{"Authorization": "Bearer <test-api-key>"}'
```

### 2. 生产部署

```bash
# 构建
pnpm build

# Docker 构建
docker build -t yliyun-mcp-server:latest .

# 通过统一 docker-compose 启动
cd /Users/qinjinlong/Documents/projects/ai/yliyunclaw
docker-compose up -d yliyun-mcp
```

### 3. MateClaw 集成配置

```yaml
# MateClaw Admin → MCP Server 管理 → 添加
name: 一粒云云盘
transport: streamable_http
url: http://yliyun-mcp:18100/mcp
headers:
  X-Internal-Service: mateclaw
  X-Forward-User-Id: ${currentUserId}
  X-Forward-Tenant-Id: ${currentTenantId}
connectTimeoutSeconds: 10
readTimeoutSeconds: 30
disclosureTier: full
```

### 4. 外部 AI 客户端配置

**Claude Desktop** (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "yliyun": {
      "type": "streamable-http",
      "url": "https://ai.example.com/mcp",
      "headers": { "Authorization": "Bearer <你的API Key>" }
    }
  }
}
```

**Codex** (`~/.codex/mcp.json`):
```json
{
  "mcpServers": {
    "yliyun": {
      "transport": "streamable-http",
      "url": "https://ai.example.com/mcp",
      "headers": { "Authorization": "Bearer <你的API Key>" }
    }
  }
}
```
