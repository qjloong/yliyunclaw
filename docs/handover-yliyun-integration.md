# 一粒云 × MateClaw 集成 — 交接文档

> 日期：2026-07-28 | 最后更新：2026-07-30 | 分支：`dev-v2` | 状态：P0 28/28 完成；P1 已完成 16/32、进行中 2、待验收 1；V3 2/34 完成；统一应用接入治理 IG 0/28 待确认
>
> 2026-07-30 实施结论：MateClaw 通过通用 MCP Runtime 发现 14 个工具，OBO 用户身份、一次性 ticket、HttpOnly Cookie、读写工具闭环均已实测。新增 `/embed/cloud-agent` 精简会话页与云盘非阻塞右侧 Drawer；当前文件可在不重载 iframe/会话的情况下实时更新活动上下文，并可携带同一会话与上下文展开完整 MateClaw。云盘附件卡仅在当前会话首次提问或宿主切换文件/文件夹后再次发送，普通追问复用已持久化的会话上下文，不重复展示同一附件。租户管理员角色和模型供应商配置现已按 Workspace 隔离。云盘本地数据库与 Redis 已切换到 `192.168.0.135`，五个开发进程现均在线。真实文件 `17485` 已使用工作空间模型 `deepseek/deepseek-chat` 完成首轮总结和无附件重传的同会话追问。最新增量已补齐云盘附件消息卡片、附件读取过程、嵌入式历史会话列表，以及按“云盘用户 + 文件/文件夹”恢复会话；从原会话重放 `file.create` 已成功把润色结果保存为云盘文件 `17490`。云盘助手不再按模板 ID 硬编码裁剪为仅云盘 MCP 工具，统一使用现有 Agent/Skill 工具绑定、渐进披露和运行时安全策略。这些能力均通过云盘专用上下文或嵌入模式开关接入，不改变普通 MateClaw 会话主链路。`CloudResourceRef`、固定引用、保存确认 UI 和 Codex 风格输入框合并仍待后续实施。

---

## 一、项目结构

当前实施涉及 **4 个代码仓库**，另有未改动的移动端：

| 仓库 | 路径 | 说明 |
|---|---|---|
| **mateclaw-dev** (MateClaw) | `D:\project\ai\mateclaw-dev` | 通用 AI 引擎，主战场 |
| **yly-saas-cdms-ai** (云盘后端) | `D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai` | 云盘 Java 后端 |
| **cloud-driver** (云盘前端) | `D:\project\yly-rag\cloud-driver` | 云盘 Vue 前端 |
| **yliyun-mcp-server** | `D:\project\ai\yliyun-mcp-server` | MCP Server（Node.js/FastMCP） |
| 云盘前端 H5 | 未改动 | 移动端 |

---

## 二、服务运行状态

### 2.1 原交接环境记录

| 服务 | 端口 | 启动方式 | 状态 |
|---|---|---|---|
| 云盘前端 | 8080 | IDE 开发服务器 | ✅ |
| 云盘后端 | 30303 | IDE (Java) | ✅ |
| MCP Server | 18100 | `cd yliyun-mcp-server && pnpm dev` | ✅ |
| MateClaw | 18088 | 命令行 Java | ✅ (非 IDEA) |

### 2.2 2026-07-28 实施前基线（已失效，仅保留问题来源）

| 服务/检查项 | 结果 | 结论 |
|---|---|---|
| 云盘前端 `:8080` | 未监听 | 当前环境不可做云盘 UI 联调 |
| 云盘后端 `:30303` | 未监听 | MCP 无真实上游可调用 |
| MCP Server `:18100` | 未监听 | MateClaw 无 MCP 服务可连接 |
| MateClaw `:18088` | 正常监听 | 仅 MateClaw 运行 |
| MateClaw 启用 MCP 数量 | 0 | 日志为 `No enabled MCP servers to initialize` |
| Flyway 当前版本 | 9011 | V9012–V9016 未进入当前运行产物 |
| `target/classes` | 缺少新增迁移及 `YliyunMcpConnector.class` | 当前进程使用旧编译产物 |

### 2.3 2026-07-29 当前运行基线

| 服务/检查项 | 结果 | 验收证据 |
|---|---|---|
| 云盘前端 `:8080` | ✅ | 首页及 `/cloud-drive/ai-assistant` HTTP 200 |
| 云盘后端 `:30303` | ✅ | 新 JAR 23 模块构建成功；数据库与 Redis 使用 `192.168.0.135`，Tomcat 正常启动，业务鉴权接口和 OpenAPI HTTP 200 |
| MCP Server `:18100/mcp` | ✅ | `/health`、`/manifest` 正常，MateClaw 发现 14 个工具 |
| MateClaw UI `:5173` | ✅ | Vite 直接加载最新 `mateclaw-ui` 源码，`/api` 代理至后端 |
| MateClaw 后端 `:18088` | ✅ | 最新 JAR 已重启，`/actuator/health` 为 `UP`，Flyway schema version `9019` |
| 云盘助手 | ✅ / 待角色 E2E | 既有租户 Workspace 与 `builtin.yliyun_assistant` 已验证；本轮 `tenantAdmin` 声明待使用真实管理员/成员账号完成最终 E2E |

Windows 启动、凭据生成、日志位置与健康检查见 `scripts/yliyun-dev/README.md`。本机开发按前后端分离运行，推荐顺序为“云盘后端 → MCP → MateClaw 后端 → MateClaw UI → 云盘前端”；`18088` 不作为开发环境的 MateClaw 前端入口。

### 2.4 `192.168.0.135` 测试中间件边界

- 当前提供的 Compose 实际位于 `D:\project\ai\wenshu\docker-compose.test.yml`，用途声明为 **WenShu 测试中间件**，不是 MateClaw 仓库内的生产部署文件。
- PostgreSQL 对外端口为 `5434`，使用命名卷 `postgres-test-data`。云盘恢复库 `cloud_drive_dev` 和应用角色 `yliyun` 当前运行在该实例中；已执行的表、序列及默认权限授权会随命名卷保留，普通容器重启不会丢失。删除卷、重建库或再次执行恢复后，仍必须重新执行应用角色授权复验。
- 该 Compose 的 MinIO API/Console 分别为 `192.168.0.135:19010/19011`，初始化 bucket 为 `wenshu`。云盘当前对象仍位于独立 endpoint `192.168.250.130:9001`、bucket `yly`；未完成对象迁移前不得把两者直接互换。
- 该 Compose 未定义 Redis。云盘配置指向的 `192.168.0.135` Redis 由其他进程或部署单元提供，不能把本文件作为 Redis 可恢复部署依据。

---

## 三、MateClaw 改动清单

### 3.1 原交接时修改文件（6 个；当时工作区未提交）

| 文件 | 改动 | 说明 |
|---|---|---|
| `.gitignore` | +5 行 | 添加 `.release`、`render-dist` 等忽略 |
| `SecurityConfig.java` | +3 行 | 放行 `/api/v1/auth/yliyun/**`、禁用 frameOptions |
| `application.yml` | +9 行 | yliyun ticket 密钥配置、Flyway out-of-order |
| `ChatInputWorkspaceBar.vue` | +28 行 | CloudFilePicker、@ mention、+ 菜单云盘选项 |
| `router/index.ts` | +11 行 | URL `?token=xxx` 自动登录 |
| `ChatConsole.vue` | +62 行 | `fileId/folderId` 参数处理、`handleAddAttachments` |

### 3.2 原交接时新增文件（19 个；当时未跟踪）

**认证模块** (`vip.mate.auth.yliyun`):
| 文件 | 说明 |
|---|---|
| `YliyunAuthController.java` | Ticket 验签/兑票（JWT + HMAC 双格式，含防重放） |
| `YliyunUserMappingService.java` | 云盘用户 → MateClaw 用户映射 |
| `McWorkspaceUserEntity.java` | `mc_workspace_user` 实体（含 yliyun 字段） |
| `repository/McWorkspaceUserMapper.java` | MyBatis Mapper |

**MCP 运行时** (`vip.mate.tool.mcp`):
| 文件 | 说明 |
|---|---|
| `controller/McpProxyController.java` | HTTP 代理：`/api/v1/mcp/proxy/{tool}` + `/status` |
| `runtime/YliyunMcpHttpClient.java` | 独立 MCP Streamable HTTP 客户端（绕过 Java SDK） |
| `runtime/YliyunMcpConnector.java` | Spring Service：连接管理 + ToolRegistry 注册 + DB 状态 |
| `runtime/YliyunMcpToolCallback.java` | Spring AI ToolCallback 包装器 |

**DB 迁移** (`db/migration`):
| 版本 | 数据库 | 说明 |
|---|---|---|
| V9012 | H2/MySQL/Kingbase | `mc_workspace_user` 表（`yliyun_user_id`、`yliyun_tenant_id`） |
| V9013 | H2/MySQL/Kingbase | `mate_agent` 补充列（`plugin_key`、`knowledge_base_ids_json`） |
| V9014 | H2/MySQL/Kingbase | `mate_mcp_server` 种子数据（yliyun-mcp 配置） |
| V9015 | H2 | 修复旧记录的 transport/headers |
| V9016 | H2 | 最终修正为 SSE 传输（已被 V9014 替代，历史遗留） |

**Agent 模板**:
| 文件 | 说明 |
|---|---|
| `templates/yliyun-assistant.json` | 云盘智能助手模板（12 工具 + 首页引导 + 验收任务） |

**前端组件**:
| 文件 | 说明 |
|---|---|
| `CloudFilePicker.vue` | @ 触发云盘文件搜索/多选 |
| `SaveToCloudDialog.vue` | 保存到云盘目录浏览/文件名输入 |

---

## 四、云盘项目改动

### 4.1 云盘后端 (`yly-saas-cdms-ai`)

| 文件 | 改动 | 说明 |
|---|---|---|
| `AiTicketController.java` | 新增 | `POST /admin-api/yliyun/ai/ticket` — HMAC-SHA256 签发，声明真实用户昵称/账号与租户名 |
| `application-local.yaml` | 修改 | 添加 `yliyun.ai.ticket-secret` 配置 |

### 4.2 云盘前端 (`yly-saas-web-cloud-driver`)

| 文件 | 改动 | 说明 |
|---|---|---|
| `AiAssistantWorkspace.vue` | 新增 | iframe 嵌入 MateClaw + ticket 传递；文件切换通过宿主消息同步，不重载会话 |
| `CloudAIAssistantDrawer.vue` | 新增 | 云盘右侧非阻塞 Drawer，左侧云盘保持可操作 |
| `CloudDriveSidebar.vue` | 修改 | 添加“AI助手”独立入口；“问数”保持原 `/cloud-drive/wenshu` 路由 |
| `CloudFileContextMenu.vue` | 修改 | 文件右键 "AI助手分析" |
| `CloudFileDetailPane.vue` | 修改 | 当前文件/文件夹直接打开 AI 助手 |
| `remaining.ts` | 修改 | `/cloud-drive/ai-assistant` 路由 |
| `yliyunAiHelper.ts` | 新增 | 助手打开、关闭和当前文件上下文事件 |

---

## 五、MCP Server 改动 (`yliyun-mcp-server`)

| 文件 | 说明 |
|---|---|
| `src/auth/index.ts` | 认证中间件（stdio 兼容 + API Key + OAuth2 + 内部服务降级） |
| `src/index.ts` | 双模式启动（`--port`/`MCP_PORT`=HTTP，无参数=stdio） |
| `package.json` | dev/start 脚本支持双模式 |
| `.env` | 云盘 API 指向 `localhost:30303` |
| `src/tools/*.ts` | 15 个工具文件（14 MCP 工具 + 索引） |

---

## 六、架构决策

### 6.1 双模式 MCP 部署（历史方案，待 P0 收敛）

```
MateClaw (Java) ──spawn──▶ yliyun-mcp-server (stdio)   ← 原内部方案
外部客户端 ──HTTP──▶ yliyun-mcp-server (:18100)         ← 原第三方方案
```

当前代码和迁移中同时出现 `stdio`、`sse`、`streamable_http`，H2 与 MySQL 的 URL 还分别使用 `/mcp` 和 `/sse`。推荐收敛为：

- 内部和外部统一使用 Streamable HTTP `/mcp`。
- stdio 仅作为 MCP Server 本地独立开发/Inspector 调试选项，不作为 MateClaw 生产主链路。
- MateClaw 只保留一套 MCP 生命周期管理和工具命名规则。

### 6.2 自定义 MCP 客户端（临时兼容实现，待 P0 替换）

Java MCP SDK (`spring-ai-starter-mcp-client` 1.1.8) 与 FastMCP httpStream 模式存在协议兼容性问题：
- FastMCP POST 响应包装为 `text/event-stream` (SSE 格式)
- Java SDK `HttpClientStreamableHttpTransport` 期望 `application/json`

临时方案：`YliyunMcpHttpClient` — 用 `java.net.http.HttpClient` 直连 FastMCP，手动解析 SSE 响应。

当前复核发现，通用 `McpClientManager` 和 `YliyunMcpConnector` 会形成两套连接、工具注册和错误处理路径。后续不再继续扩展自定义 Connector；应在通用 MCP Runtime 中解决 FastMCP 传输兼容、身份透传和工具调用问题，云盘代理也统一走该 Runtime。

### 6.3 ChatInput 组件策略（来自 `docs/git-merge-v2.md` P8）

- **ChatInput.vue** = 上游原始版本，**永不修改**（合并零冲突）
- **ChatInputWorkspaceBar.vue** = 定制组件（ChatInput 副本 + workspace bar）
- 所有新功能加在 ChatInputWorkspaceBar.vue

### 6.4 工具注册

14 个 MCP 工具通过 `ToolRegistry.registerPluginTool()` 注册，前缀 `mcp__yliyun__`：
`file.search`、`file.list`、`file.read`、`file.grep`、`file.summarize`、
`file.versions`、`file.create`、`file.save`、`file.move`、`file.delete`、
`file.tag`、`file.share_link`、`space.context`、`user.profile`

> 待修正：MateClaw 通用 MCP Runtime 使用基于 server ID 的稳定前缀，自定义 Connector 使用 `mcp__yliyun__`。两者不可并存。工具原始名、运行时名和 UI 展示名必须由同一份 capability manifest 生成。

---

## 七、完整端到端流程

```
用户登录云盘 (:8080)
  │
  ├─ 方式 1: 点击侧边栏 "AI助手"
  │   └── 非阻塞右侧 Drawer → ticket → iframe /embed/cloud-agent → 兑票 → 精简 Chat
  │
  ├─ 方式 2: 文件右键 "AI助手分析"
  │   └── Drawer + fileId → MateClaw 自动加载文件到附件
  │
  ├─ 左侧选择/预览切换文件
  │   └── contextChanged 消息 → 替换右侧待发送附件；iframe 与 conversationId 保持不变
  │
  ├─ 点击侧边栏 "问数"
  │   └── 关闭助手 Drawer → 原 `/cloud-drive/wenshu` 路由与 WenShu SSO 逻辑
  │
  └─ 在 MateClaw Chat 中:
      ├── 输入 @ → CloudFilePicker（搜索云盘文件）
      ├── 点击 + → ☁️ 选择云盘文件
      └── Agent 对话 → 调用 mcp__yliyun__* 工具操作云盘
```

---

## 八、待完成 / 已知问题

| 优先级 | 问题 | 状态 | 说明 |
|---|---|---|---|
| P0 | 当前运行产物过期 | ✅ 已解决 | MateClaw 可执行包已重建，Flyway 已执行至 `9019` |
| P0 | 五个开发进程未就绪 | ✅ 已解决 | `8080/30303/18100/18088/5173` 均通过本机检查 |
| P0 | MCP Server 未启用 | ✅ 已解决 | `yliyun-mcp` 已连接并发现 manifest 中 14 个工具 |
| P0 | MCP 双运行时 | ✅ 已解决 | 仅保留通用 `McpClientManager` 调用路径 |
| P0 | 传输和端点不一致 | ✅ 已解决 | 统一 Streamable HTTP `/mcp` |
| P0 | 用户/租户身份未闭环 | ✅ 已解决 | 每次 Tool Call 使用短时 OBO 断言并换取当前云盘用户 token |
| P0 | 租户隔离不完整 | ✅ 已解决 | TC-6 已使用真实普通成员、其他成员个人文件和伪造租户完成读、删、限流与跨租户换票验收 |
| P0 | 登录 token 暴露风险 | ✅ 已解决 | 一次性 ticket 兑 HttpOnly Cookie；重放与非法 redirect 已实测拒绝 |
| P0 | MCP 错误不可观察 | ✅ 已解决 | MCP 统一结构化错误，Picker 支持明确错误与重试 |
| P0 | Agent 创建 | ✅ 已解决 | 首次建立租户映射时幂等种子化云盘助手 |
| P1 | 云盘文件引用非一等上下文 | ⚠️ V1 已缓解 | 附件已确定性预读、显示消息卡片和执行过程，不再依赖模型猜测；签名 `CloudResourceRef`、版本和固定引用仍未完成 |
| P1 | 文件选择交互重复 | ⚠️ | 回形针、`+`、`@` 三条入口语义重叠，缺少上下文条 |
| P1 | 保存回云盘 | ⚠️ 部分完成 | Agent 直接调用 `file.create/file.save` 已可用，真实回存文件 `17490` 已复验；`SaveToCloudDialog`、目标目录/覆盖确认和完成后宿主刷新仍未接线 |
| P1 | 云盘内嵌形态过重 | ✅ 已解决 | 新增 `/embed/cloud-agent` 精简路由和非阻塞右侧 Drawer，保留完整界面入口 |
| P1 | 当前文件与助手上下文不同步 | ✅ 已解决 | 宿主消息按 channelId + parent origin 校验，替换待发送附件且不重载会话 |
| P1 | 云盘身份显示为内部映射名 | ✅ 已解决 | 内部主键继续使用 `yliyun_{tenant}_{user}`，界面改用真实昵称与租户名 |
| P1 | AI 助手与 AI 问数入口混淆 | ✅ 已解决 | 助手只打开 Drawer；问数继续走原 WenShu 路由，切换时关闭 Drawer |
| P1 | MCP Server 生产部署 | ⚠️ | 需完成 `tsc`、容器构建、健康检查和密钥注入 |
| P1 | MCP API Key 配置 | ❌ | 外部 AI 客户端需要 API Key，当前 `MCP_API_KEYS` 为空 |
| P1 | 自动化测试覆盖不足 | ⚠️ | 身份、通用 Runtime、附件预读、流事件、Workspace Provider 已有定向测试；新增真实对象存储与 TC-6 可重复脚本，Picker 已做浏览器实测；保存 UI 和跨客户端回归仍不完整 |
| P2 | Flyway V9014–V9016 清理 | ⚠️ | 发布前需按数据库是否已应用决定合并或保留，禁止直接改已发布迁移 |
| P2 | WenShu 路由 | ❌ | 尚未建立表格深度分析的显式委派和回传协议 |

---

## 九、关键配置

### MateClaw `application.yml`

```yaml
mateclaw:
  auth:
    yliyun:
      ticket-secret: ${YLIYUN_TICKET_SECRET:yliyun-ticket-secret-change-in-production}
      default-role: ${YLIYUN_DEFAULT_ROLE:user}
```

### 云盘后端 `application-local.yaml`

```yaml
yliyun:
  ai:
    ticket-secret: yliyun-ai-ticket-secret-change-me
```

> **⚠️ 生产部署前必须将 ticket-secret 改为强随机密钥，且两边一致。**

### MCP Server `.env`

```
MCP_PORT=18100
YLIYUN_API_BASE_URL=http://localhost:30303
```

---

## 十、常用调试命令

```bash
# 服务状态
curl -s http://localhost:8080/           # 云盘前端
curl -s http://localhost:30303/v3/api-docs      # 云盘后端（未启用 actuator）
curl -s http://localhost:18100/health      # MCP Server
curl -s http://localhost:18088/actuator/health  # MateClaw

# MCP 代理状态
curl -s http://localhost:18088/api/v1/mcp/proxy/status \
  -H "Authorization: Bearer <token>"

# 测试 MCP 工具（需先登录获取 token）
curl -s -X POST http://localhost:18088/api/v1/mcp/proxy/file.list \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"parentId":0,"maxResults":5}'

# 查看 MateClaw MCP 日志
grep "YliyunMCP\|Plugin tool registered" /tmp/mateclaw*.log

# 重启 MateClaw
kill -9 $(pgrep -f 'java.*MateClawApplication')
CP=$(cat /tmp/mateclaw_cp.txt)
nohup /Users/qinjinlong/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home/bin/java \
  -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8 -classpath "$CP" \
  vip.mate.MateClawApplication > /tmp/mateclaw.log 2>&1 &
```

---

## 十一、当前复核结论

### 11.1 可以保留的方案

1. 云盘作为用户、租户、文件和权限的主系统，MateClaw 不重复维护组织树。
2. MateClaw 负责 Agent、会话、工具编排、审批和记忆。
3. MCP Server 负责将云盘能力标准化，权限最终由云盘侧实时校验。
4. 文件夹问答使用 `file.list → 按需 file.read`，默认不建立持久化知识库。
5. WenShu 仅处理表格问数、NL2SQL 和复杂数据分析，不进入普通文件问答主链路。

### 11.2 必须调整的方案

1. 从“两套 MCP Runtime”收敛为 MateClaw 通用 `McpClientManager` 单一运行时。
2. 从连接级固定内部头改为每次 Tool Call 携带调用者的短时签名身份断言。
3. 从普通附件路径改为一等公民的 `CloudResourceRef`。
4. 从完整 MateClaw iframe 改为云盘右侧 `CloudAgentPanel` 精简面板。
5. 从“让 Agent 随机调用工具”改为分层、可重复、带 traceId 的 MCP 验证流程。
6. 从 URL JWT 改为一次性 code 换取 HttpOnly、SameSite 会话 Cookie。

### 11.3 目标链路

```text
云盘文件列表/预览
  └─ selectionChanged / currentPreview
       └─ CloudAgentPanel
            ├─ CloudResourceRef 上下文
            ├─ MateClaw 会话与审批
            └─ 单一 MCP Runtime
                 └─ Yliyun MCP /mcp
                      ├─ 云盘 API（实时权限校验）
                      └─ WenShu（仅表格深度分析）
```

---

## 十二、执行任务清单

### 2026-07-29 P0 验收证据摘要

- 构建与静态检查：MateClaw fat jar 构建通过；`McpIdentityForwardServiceTest` 18/18；MCP 的 `test/typecheck/build` 全部通过；云盘后端构建通过；云盘前端 `build:local` 通过；MateClaw UI 本次改动文件定向 ESLint 通过。
- 运行与迁移：五个开发进程端口均在线，MateClaw 后端健康状态为 `UP`，UI 由 Vite `5173` 提供；Flyway 已应用至 `9019`；`yliyun-mcp` 连接成功并发现 14 个工具。
- 身份与登录：真实云盘用户与租户进入 OBO 断言，MCP 换取该用户 token；ticket 首次兑换成功、二次兑换返回 401；非法外部 redirect 返回 400；URL 不携带 MateClaw 长期 JWT。
- 只读闭环：`file.list/search/read/grep/summarize` 全部返回业务成功。
- 写入闭环：在隔离临时目录完成 `create/save/move/tag/share/delete`，全部成功；临时文件夹、标签和分享均已清理。
- 分层诊断：管理页显示配置、传输、协议、身份、只读五个阶段；可显式勾选可回滚写入检查。一次真实检查六阶段全通过，同一 traceId 在 MateClaw/MCP/云盘日志分别命中 2/4/7 次。
- 固定夹具：`reset-cloud-fixtures.ps1` 可重建 `_mateclaw_p0_fixtures`；当前目录 ID `17794`，包含 txt/md/pdf/docx/xlsx 各一个，MCP `file.list` 实测返回 5 个文件。PDF/XLSX 已通过渲染检查；当前机器无 LibreOffice，DOCX 完成结构与文本校验，未完成页面渲染校验。
- 跨用户/跨租户：2026-07-30 已完成 TC-6，详见下方“对象存储、权限与嵌入面板增量复验”。

### 2026-07-29 P1 增量验收证据

- 免登录与身份：浏览器 ticket E2E 无登录页闪现；`/api/v1/auth/session` 返回真实 `displayName/tenantName`，内部鉴权用户名不再直接用于界面。
- 会话侧栏 Agent 切换去重：顶部 `AgentPickerDialog` 的数据来自当前 Workspace 的 `GET /api/v1/agents?enabled=true`，用于切换运行 Agent；原“全部员工”下拉的数据却是从 `GET /api/v1/conversations` 返回的历史会话中按 `agentId` 临时去重，只会显示有历史会话的 Agent，因而出现“四个员工”和过期名称。两者均来自上游 `001cb1a2` 的侧栏重构，不是云盘 V1 定制逻辑，但在现有交互中语义重复且数据范围不一致，现已移除下方历史会话筛选，只保留 Workspace Agent 选择器；历史会话仍完整展示，点击历史会话仍会切换到对应 Agent。
- 精简面板：`/embed/cloud-agent` 仅展示会话主体；云盘 Drawer 使用点击穿透层，左侧云盘不被遮罩阻塞。
- 文件跟随：初始 `readme.txt` 附件可见；宿主切换为另一文件后附件被替换，URL 中 `conversationId` 不变且无页面错误。
- 完整界面：从精简页展开后无登录页，保留相同 `conversationId` 与最新文件附件。
- 附件消息卡片：发送后的 `yliyun://file/{id}` / `yliyun://folder/{id}` 结构化内容片段会渲染为普通附件卡片，并标注“云盘附件”；历史消息中仅保存了 `path` 的旧数据也可正常展示。
- 读取过程：确定性附件预读会发布 `cloud_attachment_started/completed` 事件，并复用现有工具时间线组件展示；该段只写入 UI `segments`，不会伪造模型 Provider 的 `toolCalls`，不会污染下一轮 LLM 历史。
- 会话历史：嵌入页新增“会话列表”按钮，只列出当前云盘助手 Agent 的历史会话；浏览器实测侧栏可打开并切换到旧会话。
- 会话恢复：云盘宿主按真实 `userId + fileId/folderId` 保存最近 `conversationId`；Drawer 关闭时保留 iframe，重新打开同一文件优先恢复原会话。MateClaw SSO 白名单参数已允许安全透传合法 `conversationId`。
- 业务隔离：WenShu 源码、Store、Bridge、文件策略和 API 文件均无改动；问数仍进入 `/cloud-drive/wenshu`，仅在路由切换前关闭助手 Drawer。
- 构建检查：MateClaw 后端定向测试 6/6、fat jar 打包成功；MateClaw UI 与云盘前端生产构建成功；两端前端定向 ESLint 0 error；五个开发端口全部在线。
- 浏览器检查：560px 嵌入视口中仅保留一层云盘助手标题，真实租户/账号、DeepSeek、当前附件和会话列表均可见；历史会话切换后云盘附件卡片正常，未出现页面脚本错误。

### 2026-07-29 云盘文件问答与模型验收

- 文件解析：云盘附件先按 `yliyun://file/{id}` 确定性解析，再把带来源标识的内容加入会话；MCP `file.read` 已通过云盘预览流读取文件 `17485`（`兰州188测试_副本.xlsx`），解析 3 个工作表、共 2928 字符。
- 附件连续性：进入模型供应商设置时保留 `conversationId` 和云盘上下文查询参数；返回会话后可从历史恢复当前资源。云盘附件卡只在当前会话首次发送或宿主切换文件/文件夹后再次出现，普通追问依赖已持久化的消息与上下文，不重复附加同一资源；发送失败会恢复待发送附件。
- 工具上下文：`builtin.yliyun_assistant` 在图构建时只装配 `yliyun-mcp` 的 14 个工具，不再继承当前进程的 131 个全局工具；普通 Agent 行为不变。
- Qwen3 结论：按要求先使用 `ollama/qwen3:latest` 验收。当前 Ollama 实例实际上下文为 4096，收敛工具后仍连续返回空响应，因此按约定切换已配置的 `deepseek/deepseek-chat`，不把模型能力问题误报为云盘读取失败。
- Workspace 隔离：修复工作空间 Provider 被同名进程级全局 Pool/熔断状态错误剔除的问题。租户请求只使用本工作空间的供应商配置和可用性；DeepSeek 直接探测成功，延迟 1368 ms。
- Agent E2E：首轮“读取当前选择的云盘项目文档，帮我总结关键要点”在 25.4 秒内完成并持久化；同一会话不重传附件的追问在 13.2 秒内完成，正确统计“已修复 5 项、仍存在 5 项、待确认 4 项”。

### 2026-07-29 对象存储故障与恢复复验

- 18:29 之后文件 `17485` 的新一轮回归在云盘对象存储层失败：`cloud_file_object.storage_config_id=1` 映射到 `infra_file_config.id=28`，其 MinIO endpoint 仍为 `http://192.168.250.130:9001`。
- 数据库、Redis 迁移到 `192.168.0.135` 不会自动改写对象存储配置；MinIO endpoint 独立保存在云盘数据库中。故障期间该 endpoint 可建立连接但对象读取超时，云盘后端 S3 SDK 执行 4 次后报 `SdkClientException: Read timed out`，MCP `file.read` 因而无法在 60 秒内完成。
- 后续复验中 `http://192.168.250.130:9001/minio/health/live` 已返回 HTTP 200，真实文件 `17485` 通过 MateClaw → 通用 MCP → 云盘 API → MinIO 在约 1.8 秒内成功返回 XLSX 内容，说明当前阻塞已经解除。
- 现有证据只能证明当时是 MinIO 服务或网络的暂时性不可用，不能据此认定 endpoint 配错。除非对象存储本身也迁移到新服务器并完成对象同步，否则不应把 `192.168.250.130:9001` 直接替换为 `192.168.0.135`。

### 2026-07-29 PostgreSQL 恢复后的序列权限故障与修复复验

- 会话 `conv_1785323445776_b7za1n` 已成功读取文件 `17485`、完成润色，并两次调用 `file.create` 向个人空间根目录保存 `兰州188测试报告（润色版）.md`。
- 两次上传均已把 8700 bytes 写入 MinIO，但在创建 `cloud_file_index` 元数据时失败：应用账号 `yliyun` 没有 `cloud_file_index_id_seq` 的 `USAGE` 权限。云盘事务随后回滚，并通过 `cleanupOrRecord` 清理已上传对象，没有留下可见文件或孤立物理对象。
- 同一数据库还出现 `cloud_file_dynamic_id_seq`、`infra_api_error_log_seq` 权限错误。实时审计确认 `public` 下 185 个序列均由恢复数据库时的其他角色持有，`yliyun` 对其 `USAGE` 权限全部缺失，因此这不是 MCP、个人空间、MinIO或模型问题。
- 已使用对象所有者 `wenshu` 在 `192.168.0.135:5434/cloud_drive_dev` 执行 `sql/postgresql/grant-yliyun-application-role.sql` 等价授权：现有表/序列权限和 `ALTER DEFAULT PRIVILEGES` 已提交。应用账号复核结果为 185 个序列、缺失 `USAGE` 数量 0，三个已报错序列均可用。
- 从原会话持久化的工具参数恢复 4156 字符润色正文，以同一云盘用户、个人空间根目录和幂等键重放 `file.create` 成功，生成文件 ID `17490`。后续 `file.search` 找到唯一结果，`file.read` 成功读取正文，云盘上传日志完整执行到 step7c，未再出现序列权限错误或对象回滚。

### 2026-07-30 对象存储、权限与嵌入面板增量复验

- V3-A04 连续回归：真实文件 `17485` 连续三轮完成元数据预览、短时预览流、浏览器下载与 MCP `file.read`。每轮预览流和下载均为 `15,396,246` bytes，SHA-256 前缀均为 `968418d5e95864c7`；MCP 三次读取分别约 1.6–1.8 秒，内容长度和来源一致。
- TC-6.1/6.2：真实普通成员 `tenant=1/user=202` 通过一次性 SSO 映射为同租户 Workspace `member`，尝试读取、删除成员 `117` 的个人空间文件 `17472`，均返回外层 `PERMISSION_DENIED`；受保护文件随后由所有者复核仍为 `ACTIVE`。
- TC-6.3：重启后的独立突发测试中 105 次调用得到 100 次成功、5 次 `RATE_LIMITED`；可重复脚本在已有热桶状态下再次得到结构化限流。伪造 `tenant=2/user=202` 换票返回 `10200304` 且不签发 Token。
- 错误契约修复：云盘 HTTP 200 业务错误 `1042003010/无权操作` 现在映射为 `PERMISSION_DENIED`；MateClaw 通用 MCP Runtime 会从 `isError` 文本中保留结构化 `code/stage`，不再统一降级成 `MCP_TOOL_ERROR`。第三方纯文本错误继续保留兼容回退。
- 可重复证据：新增 `scripts/yliyun-dev/verify-live-cloud-security.mjs`，默认执行三轮对象内容一致性、真实 SSO、越权读删、文件存续、跨租户换票和限流测试；脚本只读取本机已忽略的 app key，不输出密钥和 Token。
- SSO Picker：`CloudFilePicker` 改为复用 MateClaw 共享 API 客户端和 `/api/v1/mcp/proxy`，同时兼容 Bearer 与 HttpOnly Cookie，不再要求 localStorage 中必须存在 `token`。全新云盘 SSO 浏览器会话实测搜索“润色版”并添加文件 `17490` 成功。
- P1-C07 浏览器回归：560px 嵌入页保留一层云盘助手标题和运行工具栏；历史会话列表可见并成功切换到 `conv_1785323445776_b7za1n`；展开完整界面保留相同 `conversationId/fileId`；宿主把当前文件切换到 `17490` 后，附件即时替换且会话 ID 不变。固定引用仍依赖 P1-A01/A02 与 P1-B04，未在本批次使用临时实现。

### 2026-07-29 V1 / V2 实施状态审计

结论：**V1 核心业务闭环已完成，但尚未达到“全部生产验收完成”；V2 未全部落地，当前主体仍是 V1 兼容实现。**

| 版本/范围 | 当前状态 | 已落地 | 未完成或阻塞 |
|---|---|---|---|
| V1 核心链路 | 核心完成 | 一次性 SSO、OBO 真实用户身份、租户 Workspace、14 个 MCP 工具、文件读写闭环、真实模型问答、云盘右侧精简面板；MinIO、迁移后 PostgreSQL 序列权限和 TC-6 均已修复复验 | 固定引用、保存确认 UI、生产部署和跨客户端完整验收未完成 |
| V1 文件解析 | 部分完成 | 文本、Markdown、XLSX/XLSM 可解析；既有 XLSX 文件真实问答成功 | PDF 仅为有限文本提取；DOC/DOCX/PPT/PPTX 等二进制 Office 格式尚无可靠服务端解析链 |
| V2 云盘专用 API | 未实施 | 云盘已有通用 `preview`、`preview-stream-url`、`content`、`download`、分享等接口可供 V1 组合调用 | 设计中的 `file/text-content`、`file/write-content`、专用 `file/search`、`space/context`、`extends/user/profile` 均未发现实现 |
| V2 MCP 升级 | 未实施 | 14 个工具已可用 | `file.search` 仍递归 `file.list`；文本读写仍使用预览/下载/上传兼容链；尚未切换到 V2 专用 API |
| V2 WenShu | 未实施 | 云盘“AI 问数”仍保持原 WenShu 业务入口 | MCP manifest 中没有 `wenshu.analyze`，MateClaw 会话内尚无显式委派、结果回传和失败回退协议 |
| V2 生产验收 | 未实施 | MateClaw 内部开发环境链路已验证 | 6 客户端 × 13 场景、Docker/密钥、性能大文件、覆盖率和生产审计均未收口 |

因此不能把 V1/V2 标记为“全部落地”。后续 V3 应以补齐 V1/V2 基础缺口为前置条件，而不是继续在 MCP 侧叠加更多递归搜索、本地二进制解析或浏览器中转等临时方案。

### 状态约定

- `待确认`：尚未获得本轮执行授权。
- `待实施`：已确认进入执行批次，但尚未开始。
- `进行中`：已有负责人正在执行。
- `待验收`：实现完成，等待按验收项验证。
- `已完成`：验收证据已回填。
- `阻塞`：依赖或外部条件不满足。

> 用户已于 2026-07-29 继续授权 P1 的精简会话页、云盘右侧面板、真实身份显示、当前文件跟随，以及 P1-E 租户角色与模型供应商隔离；P1 其余项目与 P2 仍按清单状态执行。

### 当前状态快照

| 范围 | 已完成 | 进行中 | 待验收 | 待实施/待确认 | 结论 |
|---|---:|---:|---:|---:|---|
| P0 | 28/28 | 0 | 0 | 0 | 核心链路与 TC-6 安全 E2E 全部完成 |
| P1 | 16/32 | 2 | 1 | 13 | 精简面板、附件链路、会话恢复、角色/Provider 已落地；一等资源引用、输入框收敛和保存审批未完成 |
| P2 | 0/7 | 0 | 0 | 7 | WenShu 委派、生产化、兼容性和文档收口尚未启动 |
| V3 | 2/34 | 0 | 0 | 32 | V3-A04 对象存储恢复回归、V3-D07 云盘助手工具配置去硬限制完成；媒体播放、外链、客户端动作与本地 Office 场景仍待确认实施 |
| IG | 0/28 | 0 | 0 | 28 | 问数已有应用中心闭环；AI 助手尚未纳入租户应用实例，统一 capability、三层启停强制、动态地址和 MCP 配置治理待实施 |

> 计数口径：只把已有实现且验收证据已回填的任务计为“已完成”；V1 兼容实现不等于 V2 专用 API 或 V3 资源动作协议已经完成。

### P0-A：建立可重复运行基线

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P0-A01 | 固化当前 Windows 环境的五个开发进程启动顺序和命令 | 全部 | 无 | README 中可分别启动云盘前后端、MCP、MateClaw 前后端 | 已完成 |
| P0-A02 | 完整编译 MateClaw 新代码和资源 | MateClaw | P0-A01 | 可执行包包含 Yliyun 集成类和最新迁移 | 已完成 |
| P0-A03 | 执行并核对 Flyway 迁移 | MateClaw | P0-A02 | schema history、用户映射表、MCP Server 种子记录符合预期 | 已完成 |
| P0-A04 | 启动云盘前后端、MCP 与 MateClaw 前后端 | 全部 | P0-A01 | `8080/30303/18100/18088/5173` 检查通过 | 已完成 |
| P0-A05 | 启用并连接 `yliyun-mcp` | MateClaw | P0-A03、P0-A04 | MCP 管理页状态 connected，工具数与 manifest 一致 | 已完成 |
| P0-A06 | 创建或自动种子化云盘智能助手 | MateClaw | P0-A05 | 云盘用户首次进入即可直接使用指定 Agent，无需管理员手工创建 | 已完成 |

### P0-B：收敛 MCP Runtime、传输与工具契约

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P0-B01 | 确认通用 `McpClientManager` 为唯一 Runtime | MateClaw | P0-A05 | 同一 MCP Server 只有一个连接池和生命周期管理器 | 已完成 |
| P0-B02 | 移除或禁用 `YliyunMcpConnector` 自定义注册路径 | MateClaw | P0-B01 | 不再出现 `mcp__yliyun__` 与通用前缀并存 | 已完成 |
| P0-B03 | 将云盘代理改为调用通用 Runtime | MateClaw | P0-B01 | Picker/诊断调用与 Agent 调用走相同 client 和错误模型 | 已完成 |
| P0-B04 | 统一 Streamable HTTP `/mcp` | MateClaw、MCP | P0-B01 | H2/MySQL/Docker/本地配置端点一致，不再使用 `/sse` | 已完成 |
| P0-B05 | 建立 capability manifest | MCP、MateClaw | P0-B04 | 14 个工具的原始名、Schema、风险级别、版本只有一份事实源 | 已完成 |
| P0-B06 | 统一结构化错误 | MCP、MateClaw | P0-B03 | 返回 `code/stage/retryable/suggestion/traceId`，MCP `isError` 不再被当成功 | 已完成 |
| P0-B07 | 增加调用审计和关联日志 | MCP、MateClaw、云盘 | P0-B06 | 可通过一个 traceId 串联 Agent、MCP 和云盘请求 | 已完成 |

### P0-C：打通用户身份、租户隔离和登录安全

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P0-C01 | 定义每次 Tool Call 的 OBO 身份断言 | MateClaw、MCP、云盘 | P0-B01 | 断言含 MateClaw user、Yliyun user、tenant、aud、jti、短时 exp | 已完成 |
| P0-C02 | MCP Server 验签并换取云盘用户 Token | MCP、云盘 | P0-C01 | 工具调用以真实云盘用户身份执行，不使用共享固定用户 | 已完成 |
| P0-C03 | 用户映射改为 `(tenantId, yliyunUserId)` 复合身份 | MateClaw | P0-A03 | 不同租户同 userId 不会映射为同一 MateClaw 用户 | 已完成 |
| P0-C04 | 建立租户到 Workspace 的明确映射 | MateClaw | P0-C03 | 云盘租户不会全部进入全局默认 Workspace | 已完成 |
| P0-C05 | URL ticket 改一次性 code + HttpOnly Cookie | 云盘、MateClaw | 无 | URL、localStorage、日志中不出现 MateClaw 长期 JWT | 已完成 |
| P0-C06 | redirect 白名单、参数编码、Redis 防重放 | 云盘、MateClaw | P0-C05 | 非法 redirect 被拒绝；ticket 多节点一次性使用 | 已完成 |
| P0-C07 | 跨用户/跨租户安全测试 | 全部 | P0-C02–P0-C06 | TC-6 全通过，并留存审计证据 | 已完成：真实 member 越权读删被拒绝、跨租户换票失败、限流生效；可重复脚本已落地 |

### P0-D：建立 MCP 诊断和验收体系

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P0-D01 | 创建固定测试租户、用户、目录和样例文件 | 云盘 | P0-C03 | 测试数据可重置，覆盖 txt/md/pdf/docx/xlsx | 已完成 |
| P0-D02 | 服务层诊断 | MateClaw | P0-A04 | 显示 MCP 可达性、版本、端点、延迟 | 已完成 |
| P0-D03 | 协议层诊断 | MateClaw、MCP | P0-B04、P0-B05 | `initialize/tools/list` 与 manifest 一致 | 已完成 |
| P0-D04 | 身份层诊断 | 全部 | P0-C02 | `user.profile` 返回当前测试用户和租户 | 已完成 |
| P0-D05 | 只读工具诊断 | 全部 | P0-D01、P0-D04 | `list/search/read/grep/summarize` 逐项可重复通过 | 已完成 |
| P0-D06 | 可回滚写工具诊断 | 全部 | P0-D01、P0-D04 | 在测试目录完成 create/save/move/tag/share/delete 并清理 | 已完成 |
| P0-D07 | 管理页“连接诊断”向导 | MateClaw UI | P0-D02–P0-D06 | UI 分阶段显示真实错误、修复建议与 traceId | 已完成 |
| P0-D08 | Agent 行为 E2E | MateClaw | P0-D05、P0-D06 | 固定 prompt 触发预期工具，并验证最终答案与副作用 | 已完成：DeepSeek 读取文件 17485，首轮总结与同会话追问均完成并持久化 |

### P1-A：云盘文件上下文协议

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P1-A01 | 定义 `CloudResourceRef` 前后端模型 | MateClaw、云盘前端 | P0 | 支持 file/folder/version/current-preview/mention/pinned | 待确认 |
| P1-A02 | 实现签名或不透明 `refId` 解析 | MateClaw | P1-A01、P0-C 验收 | 前端不依赖裸 tenantId，不可伪造跨租户引用 | 待确认 |
| P1-A03 | 文件引用确定性注入 Agent | MateClaw | P0-C | 发送消息时明确生成 fileId/folderId 工具上下文，不靠模型解析 URI | 已完成（V1 兼容）：服务端确定性预读并注入来源内容，UI 展示附件与执行过程；后续由 P1-A01/A02 升级为签名引用 |
| P1-A04 | 文件版本与失效状态 | MateClaw、云盘 | P1-A02 | 可显示最新、版本变化、已删除、权限失效 | 待确认 |
| P1-A05 | 文件夹渐进式读取策略 | MateClaw | P1-A03 | 先 list/summarize，再按相关性读取 3–5 个文件 | 待确认 |

### P1-B：参考 Codex 的输入框与文件交互

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P1-B01 | 增加输入框上方上下文条 | MateClaw UI | P1-A | 当前文件、已固定文件、文件夹以卡片展示 | 待确认 |
| P1-B02 | 合并重复附件入口 | MateClaw UI | P1-B01 | `@`=快速搜索，`+`=浏览/上传/引用当前文件，移除重复回形针语义 | 待确认 |
| P1-B03 | 升级云盘 Picker | MateClaw UI | P0-D、P1-A | 支持搜索、目录浏览、多选、键盘操作、明确错误与重试 | 待确认 |
| P1-B04 | 当前预览跟随/固定交互 | 云盘前端、MateClaw UI | P1-A | 未固定上下文随预览变化，固定引用保持不变 | 进行中：当前文件跟随及“首次/切换时一次性附件注入”完成，普通追问不重复展示；固定引用待实施 |
| P1-B05 | 工具过程渐进披露 | MateClaw UI | P0-B06 | 默认显示“正在读取 3 个文件”，原始工具细节可展开 | 已完成：云盘附件预读复用通用时间线展示，且不写入 Provider 工具历史 |
| P1-B06 | 大文件上下文控制 | MCP、MateClaw | P1-A | 默认 summarize/grep/maxChars，避免无界全文注入 | 待确认 |
| P1-B07 | 合并会话侧栏重复 Agent 选择器 | MateClaw UI | 无 | 仅保留 Workspace Agent 选择器；历史会话不因 Agent 过滤而丢失 | 已完成：定向 ESLint 与浏览器检查通过；“全部员工”及历史会话派生下拉已移除 |

### P1-C：云盘右侧精简 Agent 面板

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P1-C01 | 复用 Chat Runtime | MateClaw UI | P0 验收 | 完整页和嵌入页共用会话、流式、审批、附件逻辑 | 已完成：以 `embedded` 可选模式最小改造复用 `ChatConsole` |
| P1-C02 | 新建精简会话页 | MateClaw UI | P1-C01 | 仅包含单层标题栏、消息列表、状态、输入框 | 已完成：`/embed/cloud-agent`；2026-07-29 已移除云盘 Drawer 的重复 Header |
| P1-C03 | 云盘详情右侧容器 | 云盘前端 | P1-C02 | 右侧抽屉不阻塞云盘主页面，小屏可用 | 已完成：560px / 最大 94vw 非阻塞 Drawer；关闭与完整界面入口共用同一标题行 |
| P1-C04 | 定义宿主事件协议 | 云盘前端、MateClaw UI | P1-C02 | 当前上下文消息具有 channel 与 origin 校验 | 进行中：`contextChanged`、`conversation-change` 已完成，openFile/saveCompleted 待后续 |
| P1-C05 | 精简嵌入认证 | 云盘、MateClaw | P0-C05 | 面板加载不携带长期 token，无登录页闪现 | 已完成 |
| P1-C06 | “展开完整 MateClaw”能力 | MateClaw UI | P1-C02 | 保留同一会话和上下文进入完整页面 | 已完成 |
| P1-C07 | 面板浏览器 E2E | 全部 | P1-C03–P1-C06 | 选择、预览、提问、切换、固定、展开场景通过 | 待验收（仅固定引用）：免登、SSO Picker、身份、附件展示/替换、历史切换、同文件会话恢复、展开、真实模型提问及对象存储回归均已通过 |

### P1-D：保存回云盘和写操作审批

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P1-D00 | Agent 工具直接保存云盘 | MateClaw、MCP、云盘 | P0-D06 | 会话可通过 `file.create/file.save` 写入当前用户有权限的目录，并可搜索、读取复验 | 已完成：修复恢复库序列权限后，文件 `17490` 创建、搜索、读取和对象上传日志均通过 |
| P1-D01 | 接通 `SaveToCloudDialog` 调用链 | MateClaw UI | P0-B03 | 不再使用 `window.__mcpCall` 或 localhost 直连降级 | 待确认 |
| P1-D02 | 保存确认卡 | MateClaw UI | P1-D01 | 显示目标目录、文件名、覆盖/新版本和操作影响 | 待确认 |
| P1-D03 | 写操作幂等和审批 | MateClaw、MCP | P0-C 验收 | 重试不重复创建；未审批不执行写操作 | 待确认 |
| P1-D04 | 保存完成通知云盘刷新 | 云盘前端、MateClaw UI | P1-C04 | 保存后目录自动刷新并可打开新文件 | 待确认 |
| P1-D05 | 写入异常场景 | 全部 | P1-D01–P1-D04 | 重名、配额不足、权限不足、版本冲突有明确恢复路径 | 待确认 |

### P1-E：租户角色同步与模型供应商隔离

实施结论：云盘 AI ticket 已携带由服务端权限系统判定并签名的 `tenantAdmin`；云盘 `super_admin` 与 `tenant_admin` 均按管理员身份映射，MateClaw 每次 SSO 都以 `(tenantId, userId)` 对两个成员表持续对账。每个云盘租户仍确定性映射到同一 Workspace，首位租户管理员成为 owner，后续租户管理员为 admin，普通账号为 member；管理员降级时会把 owner 转交给其他有效管理员，不再通过提升全局 `mate_user.role` 绕过权限。

供应商元数据仍可复用平台目录，但租户的 API Key、OAuth、Base URL、启用状态、回退优先级、模型清单和默认模型存入带 `workspace_id` 的独立表。工作空间 API 必须显式携带并校验 `X-Workspace-Id`；admin 可配置，member/viewer 只可读取可用模型。服务器本机磁盘凭据型的 Claude Code Provider 无法实现租户级隔离，因此仅保留在平台默认工作空间，不向租户目录暴露。

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P1-E01 | AI ticket 增加服务端判定的云盘角色声明 | 云盘后端 | P0-C05 | 使用 `PermissionService` 读取 `super_admin` / `tenant_admin` 等真实管理员角色并签名，前端不可伪造 | 已完成 |
| P1-E02 | SSO 映射工作空间角色并持续对账 | MateClaw | P1-E01 | `tenant_admin → admin`、普通成员 `→ member`；每次 SSO 同步 `mc_workspace_user` 与 `mate_workspace_member`，支持升降级并清理权限缓存 | 已完成 |
| P1-E03 | 补齐 Workspace owner 与管理员转让生命周期 | 云盘、MateClaw | P1-E02 | 每个租户工作空间始终存在 owner；云盘管理员转让后原子转移或重新指定 owner | 已完成 |
| P1-E04 | 拆分全局供应商目录与工作空间供应商配置 | MateClaw | P1-E02 | Provider 元数据可全局复用；API Key、Base URL、OAuth、启用状态按 `workspace_id + provider_id` 隔离且密文保存 | 已完成 |
| P1-E05 | 模型启用、默认、回退与健康状态工作空间化 | MateClaw | P1-E04 | 不同租户可使用不同模型/default/fallback；租户配置不进入进程级全局 Pool/熔断，工作空间探测互不影响 | 已完成 |
| P1-E06 | 对齐模型设置 UI 与后端授权 | MateClaw UI、后端 | P1-E04–P1-E05 | 工作空间 admin 可配置本租户，member 只读可用模型；管理接口要求明确且已校验的 `X-Workspace-Id`，不静默回退默认工作空间 | 已完成 |
| P1-E07 | 兼容现有全局配置并执行跨租户测试 | MateClaw | P1-E04–P1-E06 | 旧配置继续归平台默认工作空间；两租户密钥、默认模型和模型清单互不可见且互不影响 | 已完成 |

P1-E 验收证据：

- 新增 H2/MySQL/Kingbase `V9019__workspace_model_provider_isolation.sql`，创建 `mate_workspace_model_provider` 与 `mate_workspace_model_config`；密钥和 OAuth token 使用既有 AES-GCM `SettingCrypto` 加密。
- H2 集成测试覆盖两个 Workspace 使用同一自定义 Provider ID 但不同密钥/模型，以及首位管理员 owner、第二位管理员 admin、普通用户 member、owner 降级转让；2/2 通过。
- Workspace scope、Provider 配置与 OpenAI OAuth 定向单元测试 75/75 通过；MateClaw 主代码及 588 个测试源编译通过。
- MateClaw UI `vite build`、云盘前端 `build:dev`、云盘后端 23 模块 reactor compile 均通过。两套前端全量类型检查仍存在仓库原有错误，本次集成文件未出现在错误列表中。
- 默认工作空间继续读取原 `mate_model_provider`、`mate_model_config`，不复制或重写既有平台密钥；新租户只写工作空间表，避免破坏性迁移。
- Agent 运行时不再用进程级全局 Provider Pool/熔断结果过滤工作空间供应商；修复后 DeepSeek 工作空间探测与真实文件连续两轮问答均成功。

### P2：WenShu、生产化和文档收口

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| P2-01 | WenShu 委派协议 | MCP、WenShu | P1-A | xlsx/csv 深度问数可显式委派并回传结构化结果 | 待确认 |
| P2-02 | WenShu 路由规则和回退 | MateClaw | P2-01 | 普通文件问答不误走 WenShu，失败可回退基础分析 | 待确认 |
| P2-03 | MCP 单元/集成测试 | MCP | P0-D | 核心 Tool 单测覆盖率 ≥80%，真实云盘集成测试通过 | 待确认 |
| P2-04 | 跨客户端兼容性 | MCP | P0 | MateClaw、Inspector、Codex/Cursor 至少完成只读场景 | 待确认 |
| P2-05 | Docker 与生产密钥 | 全部 | P0、P1 | Compose 健康检查、密钥注入、日志脱敏、滚动启动通过 | 待确认 |
| P2-06 | 性能与大文件基准 | MCP、MateClaw | P1-B06 | 建立延迟、内存、并发和大文件截断基线 | 待确认 |
| P2-07 | 文档与迁移清理 | 全部 | 其他任务 | 工具数、端点、认证、启动命令、迁移口径一致 | 待确认 |

---

## 十三、V3 会话主入口能力方案与落地计划

### 13.1 总体方案

V3 将 MateClaw 会话作为主要使用页面，但不把云盘大文件、媒体流、任意 SQL 或云盘内部页面逻辑塞进 MateClaw 主会话。会话负责意图理解、工具编排、审批和结构化结果卡片；云盘负责文件事实源、权限、对象流、原生预览/播放、审计数据和配额。

统一引入两类协议：

1. `CloudResourceRef`：不可伪造的文件/文件夹/版本引用，包含资源类型、文件 ID、版本、显示名和权限状态；对话消息、附件、工具和知识库导入共用。
2. `CloudResourceAction`：工具返回的结构化动作卡，支持 `OPEN_PREVIEW`、`PLAY`、`OPEN_LOCAL`、`DOWNLOAD`、`SHARE`、`SAVE`、`SYNC_KB`。动作只声明资源、意图、能力和短时 `actionRef`，不让模型拼接地址。嵌入模式通过带 `channelId + origin` 校验的宿主消息让云盘打开原生页面；独立 Web 页面由用户点击动作卡打开云盘应用短时地址；云盘桌面端可把同一动作转交 Tauri/本地编辑协议。

大文件和音视频不经过 LLM 上下文，也不由 MCP 完整转发。LLM 只读取元数据、结构摘要、检索片段和经过截断的文本；下载、预览和播放由云盘后端签发短时授权，由浏览器直连云盘对象流。

### 13.2 目标场景覆盖评估

| 场景 | 当前能力 | 主要缺口 | V3 方案 |
|---|---|---|---|
| 查找、获取、分析、下载、分享文件 | 搜索、列表、读取、grep、摘要、版本、分享已部分可用 | 全局检索为递归 list；缺统一元数据、下载/预览动作卡；分享等副作用审批不完整 | 补 V2 专用搜索/文本接口；新增资源详情和短时下载/预览动作；分享、删除、覆盖写入必须显式确认和幂等 |
| 生成文档、图片、音视频并保存云盘 | MateClaw 已有文档/图片/视频/音乐生成工具；文本可通过 `file.create/save` 写回 | 当前 MCP `content: string` 无法安全传输二进制生成物，MCP 也不能假定能访问 MateClaw 本地文件 | 建立不透明 `artifactRef`；MateClaw 后端到云盘后端服务端流式导入或预签名上传，包含 MIME、大小、哈希、幂等键、目标目录和进度 |
| 对话打开预览、播放音乐/视频 | 云盘已有详情、预览、播放器、`preview-stream-url`、Web/Tauri 外部打开和本地编辑协议；云盘助手已回归通用工具配置 | MCP 错把 API Base 拼成分享地址；会话工具没有结构化 open/play 结果；独立 Web、嵌入和桌面端缺统一动作分发 | 私有预览/播放不创建公开分享；工具返回 `CloudResourceAction`。嵌入模式通知云盘复用双击预览/播放器，独立 Web 显示可点击播放卡并打开云盘应用短时地址，桌面端按能力打开系统浏览器或本地 Office；媒体播放遵守用户手势和自动播放限制 |
| 服务状态、文件管理、行为、日志、套餐和用量问数/报告 | 当前 14 个文件 MCP 工具不覆盖 | 缺租户级只读语义数据集、角色约束、WenShu 委派和报告落盘 | 新建独立 `analytics.*` / WenShu 工具域；只允许租户范围的预定义指标和参数化查询，不开放任意 SQL；结果结构化为表格/图表/报告 artifact |
| 云盘资料生成知识库 | MateClaw Wiki 支持 KB CRUD、文本和文件上传 | 缺云盘资源绑定、增量同步、权限失效和版本追踪 | 新增云盘来源绑定与异步导入任务；按版本/哈希增量同步，支持文件/文件夹、手动/定时同步、权限撤销和进度审计 |

#### 13.2.1 MP4 播放、分享链接和本地 Office 的代码定位结论

1. **分享地址错误是已定位的跨层契约问题，不是模型随机生成。** 云盘后端创建外链时已经返回 `shareUrl`，云盘前端会把相对 `/share/{shareCode}` 按 `window.location.origin` 解析；但 MCP `src/cloud-api/impl.ts` 当前丢弃后端返回值，固定使用 `YLIYUN_API_BASE_URL + /share/{shareCode}` 重拼。并且 MCP 的服务端请求不携带浏览器 `Origin/Referer`，云盘后端也可能回退到 API Host。目标契约必须改为“云盘后端返回 `sharePath/publicUrl`，MCP 原样透传，客户端只使用受信的云盘应用地址解析”，禁止 MCP、模型和 MateClaw UI 使用 API Base 猜测前端地址。
2. **“播放当前私有文件”与“创建公开分享”必须分开。** 播放只需要当前用户权限下的一次性 `actionRef` 或短时预览票据；只有用户明确要求对外分享时才调用 `file.share_link`。不能为了播放 MP4 默认创建长期公共外链。
3. **会话不应依赖模型把 URL 写成 Markdown。** 当前 `file.share_link` 只返回 JSON 字符串；若最终内容落在工具 JSON、行内代码或代码块中，即使包含 URL 也不会形成稳定的业务按钮。MateClaw 的普通助手 Markdown 渲染器本身已经支持安全的 Markdown 链接和 GFM `http(s)` 自动链接，因此 V3 应以结构化动作卡为主，并补链接作为降级，不把可点击性寄托在模型排版上。
4. **`browser_use`“找不到”的模板级原因已移除。** 原 `AgentGraphBuilder.scopeYliyunAssistantTools` 对 `builtin.yliyun_assistant` 只保留来源为 `yliyun-mcp` 的 MCP Callback，且发生在通用工具绑定之后；2026-07-30 已删除该模板特判及其反向断言。云盘助手现在与普通 Agent 一样，由 Agent/Skill 工具绑定、渐进披露、运行时 Guard 和具体部署能力共同决定工具是否可用，不再硬编码仅允许云盘 MCP，也不绕过管理员配置直接开放全部工具。
5. **Browser Use 不是“打开当前用户浏览器”的正确抽象。** 它是 MateClaw 后端 JVM 所在机器上的 Playwright/CDP 自动化。开发机前后端同机时可能弹出可见 Chrome，但服务端独立部署后只会操作服务器浏览器，不能可靠控制访问 MateClaw 网页的用户电脑，更不能作为打开本机 Office 的通用方案。
6. **现有嵌入桥和桌面能力可以复用。** `CloudAgentEmbed.vue` 与云盘 `AiAssistantWorkspace.vue` 已完成 `channelId + parentOrigin` 的上下文/会话双向消息；云盘 `platformAdapter.ts` 已区分 Web 新窗口与 Tauri 外部打开，`cloudLocalEditAdapter.ts` 已能创建本地编辑任务并打开 `cloud-drive://` 协议。V3 应在这些边界上增加资源动作，而不是侵入普通 MateClaw 会话主链。

| 运行形态 | MP4/音视频 | Office 文档 | 是否要求云盘 App |
|---|---|---|---|
| 云盘 Web 内嵌 MateClaw | MateClaw 发送宿主动作，云盘按文件 ID 复用双击预览/播放组件；不离开当前页 | 优先打开云盘 OnlyOffice/预览组件 | 否 |
| 独立 MateClaw Web | 会话显示“在云盘播放”动作卡；用户点击后 `window.open` 云盘应用短时播放地址 | 打开云盘 OnlyOffice/预览页，或下载；浏览器不能无提示启动任意本地应用 | 否，但受弹窗和媒体自动播放策略限制，必须保留用户点击 |
| 云盘桌面端（Tauri） | 可复用云盘播放器，也可由原生桥调用系统默认播放器 | 经确认后创建本地编辑任务并交给已安装 Office；无客户端时回退 OnlyOffice/下载 | **只有“自动打开本机已安装 Office/系统应用”需要桌面端或已安装本地协议处理器** |

结论：MP4 播放并非仅 App 客户端可做；嵌入 Web 和独立 Web 都能完成。浏览器安全策略决定了独立 Web 的新窗口和媒体播放应由用户点击触发。真正依赖桌面客户端的是“把受保护云盘文件下载到本机、调用已安装 Office、监听修改并同步回云盘”的本地编辑闭环。

### 13.3 分阶段任务

#### V3-A：统一资源与动作协议（必须先完成）

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-A01 | 完成签名/不透明 `CloudResourceRef` | MateClaw、云盘、MCP | P1-A | 文件、文件夹、版本、当前预览和固定引用共用同一契约；跨租户伪造被拒绝 | 待确认 |
| V3-A02 | 定义 `CloudResourceAction` Schema | MateClaw、MCP、云盘前端 | V3-A01 | preview/play/download/share/save/sync-kb 可被通用会话渲染和审计 | 待确认 |
| V3-A03 | 补齐宿主动作消息 | 云盘前端、MateClaw UI | P1-C04、V3-A02 | `openFile/play/download/saveCompleted` 校验 channel、origin、资源权限 | 待确认 |
| V3-A04 | 恢复对象存储可用性并补跑回归 | 云盘 | 无 | MinIO 健康检查正常；真实文件 read/preview/download 连续通过 | 已完成：文件 `17485` 三轮 read/preview/download 内容、长度与哈希一致 |
| V3-A05 | 落地 V2 AI 友好基础 API | 云盘后端、MCP | V3-A01 | text-content、write-content、全局 search、space context、user profile 有版本化契约并替换 V1 fallback | 待确认 |

#### V3-B：常规云盘文件操作

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-B01 | 增加文件元数据、下载和预览动作工具 | MCP、云盘 | V3-A | 模型拿到结构化资源信息，浏览器使用短时授权访问，不回传文件 bytes 给 LLM | 待确认 |
| V3-B02 | 升级全局搜索与渐进分析 | 云盘、MCP | V3-A05 | 支持目录、类型、标签、时间和分页；大文件先结构/片段后全文 | 待确认 |
| V3-B03 | 写操作确认、幂等和恢复 | MateClaw、MCP | P1-D、V3-A02 | 分享、删除、覆盖、移动、公开链接均显示影响范围并可安全重试 | 待确认 |
| V3-B04 | 文件操作结果卡与云盘刷新 | MateClaw UI、云盘前端 | V3-A03、V3-B03 | 可打开、下载、复制分享链接；保存后云盘目录和详情自动刷新 | 待确认 |
| V3-B05 | 普通文件会话 E2E | 全部 | V3-B01–V3-B04 | 查找→读取→分析→下载/分享/保存全链路通过，权限失败有可恢复提示 | 待确认 |

#### V3-C：生成物保存云盘

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-C01 | 定义不透明 `artifactRef` 与生命周期 | MateClaw | V3-A | 生成物引用不暴露本地路径，支持过期、所有者、MIME、大小和 SHA-256 | 待确认 |
| V3-C02 | 云盘服务端导入/预签名上传接口 | 云盘后端 | V3-C01 | 支持文档、图片、音频、视频；校验大小、MIME、哈希、SSRF 与幂等 | 待确认 |
| V3-C03 | MateClaw artifact → 云盘传输桥 | MateClaw、MCP | V3-C02 | 服务端流式传输并上报进度，不经 LLM/浏览器 base64 中转 | 待确认 |
| V3-C04 | 保存目标与覆盖确认 UI | MateClaw UI、云盘前端 | V3-C03 | 用户选择目录、文件名、新建/新版本；完成后可直接打开 | 待确认 |
| V3-C05 | 四类生成物 E2E | 全部 | V3-C01–V3-C04 | 文档、图片、音频、视频各完成生成→确认→保存→云盘预览/播放 | 待确认 |

#### V3-D：云盘原生预览和播放

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-D01 | 统一资源打开能力判定 | 云盘后端、MCP | V3-A02 | 按 MIME、扩展名、用户权限和运行端返回 `preview/play/webOpen/desktopOpen/localEdit/download`，不让模型猜测 | 待确认 |
| V3-D02 | 修复外链与播放地址契约 | 云盘后端、MCP、MateClaw | V3-A02、IG-P0-A03–A04 | 后端返回相对 `sharePath` 或由可信云盘 Web Base 生成的 `publicUrl`；MCP 原样透传，严禁用 `YLIYUN_API_BASE_URL` 重拼；私有播放使用短时 action/launch 地址，不创建公开分享 | 待确认 |
| V3-D03 | 嵌入模式宿主预览/播放动作 | 云盘前端、MateClaw UI | V3-A03、V3-D01 | MateClaw 发送含 `actionId/channelId/resourceRef` 的请求，云盘校验 origin 和权限后复用双击预览/播放器，并回传 accepted/completed/failed；助手会话和附件不丢失 | 待确认 |
| V3-D04 | 独立 Web 预览/播放动作卡 | MateClaw UI、云盘 | V3-D01–D02 | 显示文件名、类型、来源和“在云盘播放/预览”按钮；仅在用户点击时打开云盘应用短时地址，过期可刷新，失败可复制安全链接 | 待确认 |
| V3-D05 | 会话安全链接与动作卡降级 | MateClaw UI | V3-A02 | 正常助手文本中的 Markdown 与裸 `http(s)` 可点击并带 `noopener noreferrer`；工具 JSON/代码块旁由结构化动作卡提供按钮，不执行 `javascript:`、未知协议或模型拼接 Host | 待确认 |
| V3-D06 | 实现客户端动作分发器 | MateClaw UI、云盘前端 | V3-D02–D05 | 同一 `CloudResourceAction` 按 embedded/web/desktop 分发为宿主事件、`window.open` 或原生桥；有 ACK、超时、降级和 traceId，普通会话不硬编码云盘组件逻辑 | 待确认 |
| V3-D07 | 移除云盘助手专用工具硬限制并统一 Browser Use 语义 | MateClaw | 无 | 云盘助手复用通用 Agent/Skill 工具配置、渐进披露和运行时 Guard，不按模板 ID 二次裁剪；Browser Use 若按通用配置启用，仍仅表示服务器端浏览器自动化，不描述为用户本机打开器 | 已完成：删除 `scopeYliyunAssistantTools` 模板特判；后端 JDK 21 编译、通用工具集测试基线通过 |
| V3-D08 | 桌面端本地 Office/系统应用桥 | 云盘前端、Tauri、云盘后端 | V3-D06 | 复用本地编辑任务和 `cloud-drive://` 协议；用户确认后用系统默认 Office 打开并同步修改，未安装客户端或协议时回退 OnlyOffice/下载且提示明确 | 待确认 |
| V3-D09 | 多形态、多格式与安全验收 | 全部 | V3-D03–D08 | embedded/独立 Web/桌面端覆盖 MP4、音频、PDF、Office、图片和不支持格式；验证错误 Host、过期票据、弹窗阻止、自动播放、权限撤销、跨租户伪造和无客户端降级 | 待确认 |

#### V3-E：租户问数、审计与报告

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-E01 | 定义租户只读语义数据集 | 云盘、WenShu | P0-C | 覆盖服务状态、文件、用户行为、操作日志、套餐、配额和用量；字段分级和口径明确 | 待确认 |
| V3-E02 | 实现受控 `analytics.query` | MCP、WenShu、云盘 | V3-E01 | 仅参数化指标/维度/过滤器；租户隔离、行数/时长限制、PII 脱敏，不开放任意 SQL | 待确认 |
| V3-E03 | 实现 `analytics.report` | WenShu、MateClaw | V3-E02、V3-C | 生成表格、图表和报告 artifact，可确认后保存云盘 | 待确认 |
| V3-E04 | 会话路由与 Agent 权限 | MateClaw | V3-E02 | 普通文件问答不误走 WenShu；审计能力仅授权角色可见，保留现有“AI 问数”入口 | 待确认 |
| V3-E05 | 审计场景 E2E | 全部 | V3-E01–V3-E04 | 两租户数据不可交叉；管理员报告正确；普通成员越权被拒绝并留痕 | 待确认 |

#### V3-F：云盘资料生成知识库

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| V3-F01 | 云盘来源绑定数据模型 | MateClaw | V3-A01 | 记录 workspace/kb/resource/version/hash/syncMode/status，不复制云盘 ACL 为永久权限 | 待确认 |
| V3-F02 | 文件/文件夹异步导入 | MateClaw、云盘、MCP | V3-A05、V3-F01 | 服务端提取并进入现有 Wiki raw material 流程，支持进度、失败重试和取消 | 待确认 |
| V3-F03 | 增量与定时同步 | MateClaw、云盘 | V3-F02 | 按版本/hash 只重建变化内容；支持手动/定时和文件夹新增删除 | 待确认 |
| V3-F04 | 权限变化与来源追溯 | MateClaw、云盘 | V3-F01 | 文件删除、权限撤销、租户退出后停止同步并标记失效；页面可追溯来源版本 | 待确认 |
| V3-F05 | 知识库创建/同步会话工具 | MateClaw、MCP | V3-F02–V3-F04 | 持久化操作显式确认并要求 Wiki 管理权限；创建后可立即检索验证 | 待确认 |

### 13.4 推荐实施顺序与闸门

1. V3-A04 与 P0-C07 已完成；下一步先完成 P1-A01/A02、P1-B04 的签名资源引用与固定语义，收口 P1-C07 唯一剩余场景。
2. 再将 V2 基础 API 纳入 V3-A05，并完成 V3-A/B，形成可稳定复用的资源、动作、审批和文件操作主链。
3. V3-D07 的模板级工具硬限制已先行移除，云盘助手回归通用可配置工具链，但这不替代播放主链。后续仍按 `D01 → D02 → D03/D04/D05 → D06` 完成资源动作和分发器，`D08` 做桌面增强，`D09` 作为发布闸门。
4. V3-C 可与 V3-D 的 UI/桌面增强并行，但共同依赖 `artifactRef`、短时资源动作和云盘原生预览。
5. V3-E/F 在文件链稳定后实施，分别走独立权限域；不得把审计问数混入普通文件工具，也不得把知识库同步等同一次性附件上传。
6. 每一阶段都补齐跨租户、权限撤销、幂等、超时、大文件和审计用例；阶段验收通过后再默认开放给租户。

---

## 十四、统一应用接入、SSO 与 MCP 配置治理（IG）

### 14.1 现状边界与配置归属

当前的 OAuth2 应用、云盘应用中心、MateClaw MCP 管理和 MCP 服务环境配置属于四个不同层次，不能用同一个“启用”字段替代：

| 配置域 | 当前事实源 | 当前范围 | 正确职责 |
|---|---|---|---|
| 云盘 OAuth2 应用 | `system_oauth2_client` | 平台全局（`@TenantIgnore`） | 管理 `client_id`、密钥、grant、scope、redirect URI 和 Token 生命周期；不承担租户应用开关 |
| 云盘应用中心 | `cloud_app_market`、`cloud_app_instance`、应用配置项 | 应用清单 + 租户实例 | 管理应用安装、租户启停、动态服务地址、角色/文件策略、健康状态和生命周期 |
| MateClaw MCP 管理 | `mate_mcp_server` | 当前全局，无 `workspace_id/tenant_id` | 管理 MCP transport、URL、headers、启停、连接测试和工具发现；平台共享连接不应由普通租户管理员修改 |
| MCP 服务配置 | 部署环境变量/Secret | 单个 MCP 部署实例 | 管理云盘 API 地址、内部连接 Token、OBO 公钥/issuer/audience、云盘换票 appKey、限流和日志 |

问数 `wenshu_integration` 已具备“系统租户维护平台连接、当前租户维护策略、enable/disable 远端生命周期、capability 控制菜单、一次性 ticket + 短时 delegation”的闭环，应作为统一应用接入的兼容基线。

AI 助手当前仍有四个未统一点：

1. 云盘前端固定展示 AI 助手菜单，没有读取租户应用 capability。
2. MateClaw 地址来自前端构建变量 `VITE_AI_BASE_URL`，地址变化需要重新构建云盘前端。
3. `/admin-api/yliyun/ai/ticket` 只依赖已登录用户，尚未强制检查当前租户的应用实例、启用状态、健康状态和使用权限。
4. MCP OBO 换取云盘 Token 依赖全局 `YLIYUN_APP_KEY`，尚未在换票或 Tool Call 阶段校验当前租户是否已启用 AI 助手。

### 14.2 统一目标流程

云盘应用中心作为业务配置唯一事实源，OAuth2/OIDC 只作为认证基础设施：

```text
平台管理员
  └─ 配置应用清单、服务/API/嵌入地址、可信 Origin、认证协议和密钥

租户管理员
  └─ 安装/启停应用、配置角色和文件/问数策略
       └─ capability 决定菜单与入口
            └─ 一次性 launch ticket
                 └─ MateClaw / WenShu 建立应用会话
                      └─ OBO / delegation 代表当前云盘用户调用业务能力
                           └─ 云盘后端再次校验租户应用状态、用户权限和资源 ACL
```

应用关闭必须在三个后端闸门同时生效：

1. **入口闸门**：capability 不再暴露菜单和启动地址。
2. **会话闸门**：拒绝签发新的 SSO/launch ticket，并撤销已有 delegation、应用会话或相关缓存。
3. **工具闸门**：MCP OBO 换票和受保护 Tool Call 再次校验租户 entitlement；不能仅依靠隐藏前端入口。

AI 助手应注册为 `mateclaw_ai_assistant` 应用扩展，平台字段至少包含 MateClaw API 地址、嵌入地址、allowed origin、ticket/OIDC 配置和关联 MCP audience；租户字段至少包含 enabled、allowed roles、文件读写/分享/删除范围、打开方式和导航显示策略。前端只消费服务端 capability，不再持有外部服务地址或密钥。

### 14.3 IG-P0：租户开关、安全闸门与动态配置（最高优先级）

#### IG-P0-A：AI 助手纳入应用中心

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| IG-P0-A01 | 固化应用、认证和凭据责任矩阵 | 云盘、MateClaw、MCP、WenShu | 无 | 文档和配置模板明确 SSO ticket secret、MCP internal token、OBO 密钥对、云盘 appKey、WenShu client secret 的持有方、用途、轮换和禁止复用规则 | 待确认 |
| IG-P0-A02 | 注册 `mateclaw_ai_assistant` 应用扩展 | 云盘后端 | IG-P0-A01 | 应用中心可安装 AI 助手并为每个租户创建独立 `cloud_app_instance`，不影响现有 WenShu 扩展 | 待确认 |
| IG-P0-A03 | 定义平台连接配置与租户策略 Schema | 云盘后端 | IG-P0-A02 | 系统租户维护地址、Origin 和认证配置；租户管理员只维护 enabled、角色和文件能力策略；密钥加密且不回显 | 待确认 |
| IG-P0-A04 | 建立通用应用 capability 契约 | 云盘后端、云盘前端 | IG-P0-A02–A03 | capability 同时反映 installed/enabled/healthy/allowed，返回服务端解析后的安全 launch 元数据，不泄露密钥 | 待确认 |
| IG-P0-A05 | 云盘前端改为 capability 驱动菜单和动态地址 | 云盘前端 | IG-P0-A04 | AI 助手与问数分别按 capability 展示；移除 AI 助手“所有用户固定可见”和 `VITE_AI_BASE_URL` 运行依赖；地址修改无需重新构建前端 | 待确认 |
| IG-P0-A06 | 实现 AI 助手 enable/disable/health 生命周期 | 云盘、MateClaw | IG-P0-A03–A04 | 启用前健康检查并 provision 租户 Workspace/Agent；停用先本地 fail closed，再通知远端；状态和失败原因可刷新、可审计 | 待确认 |

#### IG-P0-B：统一 SSO 与三层强制开关

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| IG-P0-B01 | 定义通用一次性 launch ticket 契约 | 云盘、MateClaw、WenShu | IG-P0-A01 | 统一 appKey、tenant/user、state/nonce、aud、iat/exp、jti、configVersion 和 launchContext；ticket 不包含长期 Token | 待确认 |
| IG-P0-B02 | AI 助手出票增加租户 entitlement 与权限校验 | 云盘后端 | IG-P0-A04、IG-P0-B01 | 未安装、停用、不健康、无角色权限均无法出票；拒绝原因结构化且留审计日志 | 待确认 |
| IG-P0-B03 | MateClaw 验票绑定 app/audience/configVersion | MateClaw | IG-P0-B01 | 仅接受允许的 appKey/audience/redirect/origin；一次性、防重放、过期和配置版本失效测试通过 | 待确认 |
| IG-P0-B04 | MCP OBO 换票和 Tool Call 增加租户应用校验 | MCP、云盘 | IG-P0-A04、IG-P0-B01 | 停用租户即使绕过菜单或持有 MateClaw Cookie也无法换取新云盘 Token或调用受保护工具；云盘 ACL 仍是最终权限源 | 待确认 |
| IG-P0-B05 | 停用时撤销 delegation、应用会话和身份缓存 | 云盘、MateClaw、MCP、WenShu | IG-P0-A06、IG-P0-B04 | 停用在目标 SLA 内阻断新旧调用；缓存键包含 tenant/app/configVersion，不出现跨租户误清理 | 待确认 |
| IG-P0-B06 | 密钥加密、双密钥轮换和安全审计 | 云盘、MateClaw、MCP、WenShu | IG-P0-A03、IG-P0-B01 | current/previous 窗口可无中断轮换；管理接口不回显明文；URL、日志、错误和前端构建产物不含密钥 | 待确认 |

#### IG-P0-C：MCP 配置治理和安全验收

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| IG-P0-C01 | 将平台共享 MCP 配置限制为平台管理员 | MateClaw | 无 | `mate_mcp_server` 全局连接只有平台管理员可增删改、启停和修改 headers；租户管理员只配置本 Workspace 的 Agent/模型/应用授权 | 待确认 |
| IG-P0-C02 | 固化一粒云 MCP 独立部署 Profile | MateClaw、MCP、云盘 | IG-P0-A01、IG-P0-C01 | 配置模板覆盖 URL/transport、internal token、OBO 私钥/公钥、issuer/audience、云盘 API/appKey；启动时 fail-fast 校验错误配对 | 待确认 |
| IG-P0-C03 | 拆分通用 MCP 与一粒云身份诊断 | MateClaw、MCP | IG-P0-C02 | 管理页分别显示 transport/tools、连接凭据、OBO、云盘换票、tenant entitlement、只读和可回滚写入阶段 | 待确认 |
| IG-P0-C04 | 完成应用启停和认证跨租户 E2E | 全部 | IG-P0-A、IG-P0-B、IG-P0-C03 | 覆盖两租户独立启停、动态改址、密钥轮换、停用旧会话、普通成员越权、伪造 audience/appKey 和 MCP 重连；保留 traceId 证据 | 待确认 |

### 14.4 IG-P1：通用应用连接框架与运维治理

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| IG-P1-01 | 抽象通用应用连接器 SPI | 云盘后端 | IG-P0 验收 | Manifest、配置 Schema、capability、health、provision/deactivate、ticket 和 revoke 具有稳定版本化接口 | 待确认 |
| IG-P1-02 | 将 WenShu 适配到通用契约 | 云盘、WenShu | IG-P1-01 | 保持现有问数入口、文件策略和 delegation 行为不变；移除仅因应用不同产生的重复控制器/前端判断 | 待确认 |
| IG-P1-03 | 建立应用到 OAuth2 Client/SSO Profile 的显式绑定 | 云盘 | IG-P0-B01、IG-P1-01 | OAuth2 Client 继续平台全局；租户应用实例只引用 profile，不通过全局 client status 代替租户启停 | 待确认 |
| IG-P1-04 | 配置版本、事件和缓存失效机制 | 云盘、MateClaw、MCP、WenShu | IG-P1-01 | 配置修改产生单调版本和审计事件；各服务按 tenant/app/version 精确刷新，不依赖重启或固定 TTL 等待 | 待确认 |
| IG-P1-05 | 建立应用依赖模型 | 云盘、MateClaw | IG-P1-01 | 可声明依赖 MCP capability、Agent 模板、模型 Provider、WenShu 和最低协议版本；启用前自动诊断依赖 | 待确认 |
| IG-P1-06 | 应用连接与租户运行状态管理页 | 云盘前端、MateClaw UI | IG-P1-04–P1-05 | 平台管理员看连接/版本/密钥轮换状态；租户管理员只看本租户启停、健康和策略，不可见平台密钥 | 待确认 |
| IG-P1-07 | 兼容迁移和回滚 | 全部 | IG-P1-01–P1-06 | 现有 WenShu 配置、AI ticket、MateClaw 用户映射和 MCP Server 记录原地迁移；支持灰度双读、回滚且不重建用户/Workspace | 待确认 |

### 14.5 IG-P2：MCP 业务扩展与低代码工具发布

| ID | 任务 | 责任仓库 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| IG-P2-01 | 将 MCP 工具注册拆为版本化 `ToolProvider` 模块 | MCP | IG-P0-C02 | 文件、全文检索、审计、WenShu 等工具域可独立测试和启停；manifest 标明版本、风险、权限和依赖 | 待确认 |
| IG-P2-02 | 增加受控的只读 OpenAPI/声明式 Tool 映射 | MCP、云盘 | IG-P2-01 | 白名单 API 可通过 Schema 发布而无需修改 MateClaw；禁止任意 URL、任意 Header、任意 SQL 和未声明副作用 | 待确认 |
| IG-P2-03 | 统一 Tool 风险、审批和租户授权策略 | MateClaw、MCP、云盘 | IG-P2-01 | read/write/share/delete/audit 分级；Agent 绑定、租户应用策略和云盘权限三者同时满足才可调用 | 待确认 |
| IG-P2-04 | 发布全文检索工具域 | 云盘、MCP | V3-A05、IG-P2-01–P2-03 | 云盘提供版本化全文检索 API；MCP 发布分页、过滤、片段和来源明确的工具；不再用递归 `file.list` 代替全文检索 | 待确认 |
| IG-P2-05 | 发布审计、状态、套餐和报告工具域 | 云盘、MCP、WenShu、MateClaw | V3-E、IG-P2-01–P2-03 | 使用预定义指标和参数化查询，禁止任意 SQL；租户管理员/审计员权限、PII 脱敏、报告 artifact 和跨租户 E2E 通过 | 待确认 |

### 14.6 实施优先级、批次和发布闸门

1. **批次 0：口径冻结（IG-P0-A01）**。先确定四种配置域和五类凭据的唯一事实源、轮换与责任人；在此之前不新增新的共享密钥或前端地址变量。
2. **批次 1：AI 助手应用中心化（IG-P0-A02–A06）**。优先解决租户级安装、启停、动态地址、capability 和健康状态；这是后续统一 SSO 的入口基础。
3. **批次 2：安全强制（IG-P0-B01–B06）**。出票、验票、MCP 换票/Tool Call 三层同时落闸，并补停用撤销和密钥轮换；完成前不得把 AI 助手应用中心开关宣称为安全边界。
4. **批次 3：MCP 治理与验收（IG-P0-C01–C04）**。把全局 MCP 配置收回平台管理员，固化独立部署 Profile 和分层诊断，完成两租户真实 E2E 后方可默认开放。
5. **批次 4：框架复用（IG-P1）**。在 AI 助手 P0 闭环稳定后抽象通用 SPI，再让 WenShu 兼容迁移，避免先抽象后反复修改；全程保持问数现有入口和业务逻辑不回退。
6. **批次 5：MCP 扩展（IG-P2）**。先模块化和安全策略，再做声明式只读映射；全文检索与审计报告分别复用 V3-A05、V3-E，不重复建设第二套 API。
7. P1-A/P1-B 的 `CloudResourceRef` 与固定引用可在 IG-P0 接口冻结后并行；V3-B/C/D 的资源动作主链不依赖 IG-P1 完成，但所有新租户开放必须经过 IG-P0 的 entitlement 闸门。

发布闸门：

- IG-P0-C04 未通过前，只允许测试租户使用新的 AI 助手应用实例。
- 动态地址必须经过协议、Origin/SSRF、健康检查和回滚校验，不能把任意租户输入直接作为后端请求目标。
- MCP 新工具被发现不等于自动授权；仍需通过平台 MCP 启用、Agent 工具绑定、租户应用策略和云盘资源权限。
- 当前实现新增工具仍需重新构建/部署 MCP；完成 IG-P2-01/P2-02 后，仅批准的简单只读映射可免代码重打包，复杂认证、转换和副作用工具仍走版本化模块发布。

---

## 十五、执行确认闸门

以下架构决策已确认，P0 与已授权的 P1 子项已据此实施：

1. **MCP Runtime**：采用 MateClaw 通用 `McpClientManager`，停止扩展 `YliyunMcpConnector`。
2. **传输协议**：内部和外部统一 Streamable HTTP `/mcp`；stdio 仅用于本地独立调试。
3. **身份协议**：每次 Tool Call 使用短时签名 OBO token，包含 Yliyun 用户和租户声明。
4. **登录协议**：一次性 code 换 HttpOnly、SameSite Cookie，不再把 MateClaw JWT 放在 URL/localStorage。
5. **嵌入形态**：优先实现可复用 `CloudAgentPanel`；若跨仓库独立部署必须使用 iframe，则仅嵌入精简路由并启用严格 origin 协议。
6. **当前执行批次**：P0 核心已实施；P1 已落地精简面板、真实身份显示、当前文件跟随、附件一次性注入、会话历史恢复、Agent 切换去重、租户角色同步与工作空间级模型供应商隔离；云盘助手工具配置已回归 MateClaw 通用 Agent 工具链，保持普通会话主逻辑最小改动。V1 生产收口、V2、V3 和 IG 尚未全部完成。
7. **应用治理口径**：云盘应用中心是租户启停和业务配置事实源；OAuth2 Client 只负责平台认证；MateClaw 全局 MCP 连接与 MCP 服务密钥不得下放给普通租户管理员。

下一次确认建议用语：

> 确认先实施 IG-P0-A01–A06 与 IG-P0-B01–B06，把 AI 助手纳入应用中心并完成租户 capability、动态地址和三层启停强制；随后实施 IG-P0-C01–C04 的 MCP 平台治理与跨租户验收。`CloudResourceRef`/固定引用可在接口冻结后并行推进。

未经下一次确认，不执行以下动作：

- 不在未确认前继续 P1-A 的签名 `CloudResourceRef`、固定引用和输入框入口合并。
- 不在未确认前实施 V3 的云盘新 API、artifact 传输、审计问数或知识库同步。
- 不在未确认前创建 `mateclaw_ai_assistant` 租户应用实例、迁移现有 AI ticket 或改变 MCP 管理权限。
- 不创建额外真实租户/用户或写入非临时业务数据。
- 不改动生产密钥、API Key 或生产部署配置。
- 不清理、合并或重写已可能发布的 Flyway 迁移。
