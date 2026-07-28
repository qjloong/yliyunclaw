# 一粒云 × MateClaw 集成 — 交接文档

> 日期：2026-07-28 | 分支：`dev-v2` | 状态：核心链路已打通，待验收

---

## 一、项目结构

共涉及 **5 个代码仓库**：

| 仓库 | 路径 | 说明 |
|---|---|---|
| **yliyunclaw** (MateClaw) | `/Users/qinjinlong/Documents/projects/ai/yliyunclaw` | 通用 AI 引擎，主战场 |
| **yly-saas-cdms-ai** (云盘后端) | `/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-cdms-ai` | 云盘 Java 后端 |
| **yly-saas-web-cloud-driver** (云盘前端) | `/Users/qinjinlong/Documents/projects/ai/saas/yly-saas-web-cloud-driver` | 云盘 Vue 前端 |
| **yliyun-mcp-server** | `/Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server` | MCP Server (Node.js/FastMCP) |
| 云盘前端 H5 | 未改动 | 移动端 |

---

## 二、服务运行状态

| 服务 | 端口 | 启动方式 | 状态 |
|---|---|---|---|
| 云盘前端 | 8080 | IDE 开发服务器 | ✅ |
| 云盘后端 | 30303 | IDE (Java) | ✅ |
| MCP Server | 18100 | `cd yliyun-mcp-server && pnpm dev` | ✅ |
| MateClaw | 18088 | 命令行 Java | ✅ (非 IDEA) |

### MateClaw 重启命令

```bash
# Kill + restart（当前非 IDEA 模式）
kill -9 $(pgrep -f 'java.*MateClawApplication')
CP=$(cat /tmp/mateclaw_cp.txt)
nohup /Users/qinjinlong/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home/bin/java \
  -XX:TieredStopAtLevel=1 -Dspring.output.ansi.enabled=always \
  -Dfile.encoding=UTF-8 -classpath "$CP" \
  vip.mate.MateClawApplication > /tmp/mateclaw.log 2>&1 &
```

> **注意**：当前是从命令行直接启动（非 IDEA）。如果在 IDEA 中启动，Flyway 会自动执行迁移，且会自动编译新 Java 文件。

### MCP Server 启动命令

```bash
cd /Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server
# 开发模式（HTTP Streamable）
pnpm dev
# 或手动指定端口
MCP_PORT=18100 npx tsx src/index.ts
# 生产模式
pnpm build && pnpm start
```

---

## 三、MateClaw 改动清单

### 3.1 修改文件（6 个，工作区未提交）

| 文件 | 改动 | 说明 |
|---|---|---|
| `.gitignore` | +5 行 | 添加 `.release`、`render-dist` 等忽略 |
| `SecurityConfig.java` | +3 行 | 放行 `/api/v1/auth/yliyun/**`、禁用 frameOptions |
| `application.yml` | +9 行 | yliyun ticket 密钥配置、Flyway out-of-order |
| `ChatInputWorkspaceBar.vue` | +28 行 | CloudFilePicker、@ mention、+ 菜单云盘选项 |
| `router/index.ts` | +11 行 | URL `?token=xxx` 自动登录 |
| `ChatConsole.vue` | +62 行 | `fileId/folderId` 参数处理、`handleAddAttachments` |

### 3.2 新增文件（19 个，未跟踪）

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
| `AiTicketController.java` | 新增 | `POST /admin-api/yliyun/ai/ticket` — HMAC-SHA256 签发 |
| `application-local.yaml` | 修改 | 添加 `yliyun.ai.ticket-secret` 配置 |

### 4.2 云盘前端 (`yly-saas-web-cloud-driver`)

| 文件 | 改动 | 说明 |
|---|---|---|
| `AiAssistantWorkspace.vue` | 新增 | iframe 嵌入 MateClaw + ticket 传递 + fileId/fileName |
| `AiAssistantLayout.vue` | 新增 | 布局容器 |
| `CloudDriveSidebar.vue` | 修改 | 添加 "AI助手" 导航按钮 |
| `CloudFileContextMenu.vue` | 修改 | 文件右键 "AI助手分析" |
| `remaining.ts` | 修改 | `/cloud-drive/ai-assistant` 路由 |
| `yliyunAiHelper.ts` | 新增 | AI 助手辅助工具函数 |

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

### 6.1 双模式 MCP 部署

```
MateClaw (Java) ──spawn──▶ yliyun-mcp-server (stdio)   ← 内部使用
外部客户端 ──HTTP──▶ yliyun-mcp-server (:18100)         ← 第三方接入
```

### 6.2 自定义 MCP 客户端（绕过 Java SDK）

Java MCP SDK (`spring-ai-starter-mcp-client` 1.1.8) 与 FastMCP httpStream 模式存在协议兼容性问题：
- FastMCP POST 响应包装为 `text/event-stream` (SSE 格式)
- Java SDK `HttpClientStreamableHttpTransport` 期望 `application/json`

解决方案：`YliyunMcpHttpClient` — 用 `java.net.http.HttpClient` 直连 FastMCP，手动解析 SSE 响应。

### 6.3 ChatInput 组件策略（来自 `docs/git-merge-v2.md` P8）

- **ChatInput.vue** = 上游原始版本，**永不修改**（合并零冲突）
- **ChatInputWorkspaceBar.vue** = 定制组件（ChatInput 副本 + workspace bar）
- 所有新功能加在 ChatInputWorkspaceBar.vue

### 6.4 工具注册

14 个 MCP 工具通过 `ToolRegistry.registerPluginTool()` 注册，前缀 `mcp__yliyun__`：
`file.search`、`file.list`、`file.read`、`file.grep`、`file.summarize`、
`file.versions`、`file.create`、`file.save`、`file.move`、`file.delete`、
`file.tag`、`file.share_link`、`space.context`、`user.profile`

---

## 七、完整端到端流程

```
用户登录云盘 (:8080)
  │
  ├─ 方式 1: 点击侧边栏 "AI助手"
  │   └── /cloud-drive/ai-assistant → ticket → iframe MateClaw → 兑票 → Chat
  │
  ├─ 方式 2: 文件右键 "AI助手分析"
  │   └── /cloud-drive/ai-assistant?fileId=xxx&fileName=xxx
  │       └── ticket + fileId → MateClaw 自动加载文件到附件
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
| P0 | 前端验证 | ⚠️ | @ 触发、+ 菜单、文件右键跳转需浏览器实机验证 |
| P0 | Agent 创建 | ⚠️ | 云盘智能助手模板需通过 UI 创建 Agent 实例 |
| P1 | MCP Server 生产部署 | ⚠️ | `pnpm build && pnpm start` 需要先 `tsc` 编译 |
| P1 | MCP API Key 配置 | ❌ | 外部 AI 客户端需要 API Key，当前 `MCP_API_KEYS` 为空 |
| P1 | MCP Server 单元测试 | ❌ | `tests/unit/` 为空，覆盖率 0% |
| P2 | Flyway V9014–V9016 清理 | ⚠️ | 多次迭代产生多个迁移，合并后可精简为 1 个 |
| P2 | `git merge upstream/dev` 冲突 | ⚠️ | 按 `docs/git-merge-v2.md` 第 12 节操作，约 40 分钟 |

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
curl -s http://localhost:30303/actuator/health  # 云盘后端
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
