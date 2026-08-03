# 一粒云集成本机运行基线（Windows）

当前工作区固定为：

| 服务 | 仓库 | 端口 |
|---|---|---:|
| MateClaw UI（开发） | `D:\project\ai\mateclaw-dev\mateclaw-ui` | 5173 |
| MateClaw 后端/API | `D:\project\ai\mateclaw-dev\mateclaw-server` | 18088 |
| Yliyun MCP | `D:\project\ai\yliyun-mcp-server` | 18100 |
| 云盘前端 | `D:\project\yly-rag\cloud-driver` | 8080 |
| 云盘后端 | `D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai` | 30303 |

## 1. 初始化凭据与检查环境

```powershell
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\generate-credentials.mjs
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify.ps1
```

生成的 OBO 密钥、ticket secret 和 MCP 内部 app key 位于 `data\yliyun-dev`，已被 Git 忽略。不要提交或复制到生产环境。

## 2. 启动顺序

本机联调包含五个独立进程，MateClaw 前后端分别启动。脚本会按需构建、
注入同一组本机凭据、后台启动并等待服务就绪。推荐按以下顺序执行：

```powershell
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-cloud-backend.ps1
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-mcp.ps1
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-mateclaw.ps1
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-mateclaw-ui.ps1
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-cloud-frontend.ps1
```

已有可执行产物且只需重启时，三个需要构建的脚本均支持 `-SkipBuild`，例如：

```powershell
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-cloud-backend.ps1 -SkipBuild
```

需要立即验证最新 MateClaw 前端源码时，可单独启动 Vite：

```powershell
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\start-mateclaw-ui.ps1
```

浏览器访问 `http://localhost:5173/`。该开发服务会把 `/api` 和 WebSocket 请求代理到
`http://localhost:18088`，因此仍需保持 MateClaw 后端在线。`18088` 是后端/API
入口，不作为开发环境的 MateClaw 前端访问地址。

日志统一写入 `data\yliyun-dev\logs`。也可继续从 IntelliJ 启动两个 Java 服务，但必须在 Run Configuration 中设置相同的 `YLIYUN_TICKET_SECRET` 和 `YLIYUN_MCP_APP_KEY`，并为 MateClaw 设置 `MATECLAW_MCP_OBO_PRIVATE_KEY_PEM`、为 MCP 设置 `MATECLAW_OBO_PUBLIC_KEY_PEM`。

## 3. 验证

```powershell
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify.ps1 -RequireServices
curl.exe http://127.0.0.1:18100/manifest
curl.exe http://127.0.0.1:18088/actuator/health
```

登录 MateClaw 后，在“设置 → MCP 连接”的 `yliyun-mcp` 卡片点击“分层诊断”。诊断必须依次通过：

1. Streamable HTTP `/mcp` 配置；
2. 网络与传输；
3. `initialize/tools/list` 发现 14 个工具；
4. `user.profile` 返回当前云盘用户与租户。

第四步失败通常表示当前 MateClaw 用户尚未通过云盘 ticket 登录，或
`mc_workspace_user` 没有唯一的 `(tenantId, yliyunUserId)` 映射。

## 4. 重置验收夹具

五个开发进程在线后，使用本机云盘开发账号重建固定验收目录：

```powershell
powershell -ExecutionPolicy Bypass -File D:\project\ai\mateclaw-dev\scripts\yliyun-dev\reset-cloud-fixtures.ps1 -CloudPassword '<本机开发账号密码>'
```

脚本会仅删除并重建根目录下名称完全匹配的 `_mateclaw_p0_fixtures`，然后上传
txt、md、pdf、docx、xlsx 各一个并校验文件列表。构建产物位于已忽略的
`data\yliyun-dev\fixture-build`，不得提交真实账号密码或生成的测试数据。

PDF 和 XLSX 会在生成后渲染检查。DOCX 的结构和文本始终会校验；若本机已安装
LibreOffice，脚本还会执行页面渲染，否则会明确提示跳过 DOCX 视觉校验。

## 5. 真实对象存储与 TC-6 安全回归

五个服务启动后，可执行：

```powershell
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify-live-cloud-security.mjs
```

脚本使用 `data/yliyun-dev/mcp-app-key.txt` 换取短时测试用户 Token，不输出密钥或
Token。默认连续执行三轮 `read/preview/download` 一致性校验，并验证：

- 普通成员读取其他成员个人文件返回 `PERMISSION_DENIED`；
- 普通成员删除其他成员个人文件返回 `PERMISSION_DENIED`，原文件保持 `ACTIVE`；
- 伪造其他租户身份不能换取 Token；
- 105 次突发工具调用能够得到结构化 `RATE_LIMITED`。

默认夹具 ID 来自 2026-07-30 的共享测试库，可用以下环境变量覆盖：

```text
YLIYUN_TEST_TENANT_ID
YLIYUN_TEST_OWNER_USER_ID
YLIYUN_TEST_ATTACKER_USER_ID
YLIYUN_TEST_VICTIM_USER_ID
YLIYUN_TEST_READABLE_FILE_ID
YLIYUN_TEST_PROTECTED_FILE_ID
YLIYUN_TEST_FORGED_TENANT_ID
YLIYUN_TEST_READ_CYCLES
YLIYUN_TEST_RATE_BURST
```

## 6. AI ticket 双密钥无中断轮换

云盘后端只用 `current` 签发 ticket 和撤销通知；MateClaw 在轮换窗口内同时接受
`current/previous`。本机密钥文件位于已忽略的 `data\yliyun-dev`，脚本只输出 key id，
不会输出 secret。

先生成新 current，并把旧 current 保留为 previous：

```powershell
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\generate-credentials.mjs --prepare-ticket-rotation
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify-credentials.mjs
```

然后严格按以下顺序部署：

1. 先重启 MateClaw 后端，使其加载 `current=new/previous=old`；云盘后端暂不重启。
2. 执行 `node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify-ticket-rotation.mjs`，应看到旧 key id 且 SSO 成功。
3. 再重启云盘后端，使其改用新 current 签发。
4. 再次执行验证脚本，应看到新 key id 且 SSO 成功。
5. 从云盘切换到新 current 起至少保留 previous 15 分钟，并确认撤销重试和验票日志无异常。

轮换窗口结束后，将 previous 移到可恢复的本机备份，再重启 MateClaw 和云盘后端：

```powershell
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\generate-credentials.mjs --retire-ticket-previous
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify-credentials.mjs
```

在 previous 尚未退役时再次执行 `--prepare-ticket-rotation` 会直接失败，避免覆盖仍在使用的
旧密钥。生产环境应把四个值放入 Secret Manager/KMS 注入的环境变量
`YLIYUN_TICKET_SECRET_CURRENT/PREVIOUS`、`YLIYUN_TICKET_KEY_ID_CURRENT/PREVIOUS`，
不得提交到代码仓库、前端配置或应用中心业务配置。

## 7. AI 助手动态地址回归

五个服务在线且租户 1 已启用 AI 助手时执行：

```powershell
node D:\project\ai\mateclaw-dev\scripts\yliyun-dev\verify-dynamic-ai-address.mjs
```

脚本会使用系统租户管理员短 Token 验证：不可达候选 API 地址不落库且不递增
`configVersion`；MateClaw 浏览器/API 地址切换后 capability、SSO 和版本失效立即生效；
最后在 `finally` 中恢复原浏览器地址、API 地址和 allowed origin。每次配置变更都会按设计
撤销 MCP 专用 delegation，因此脚本会为每个管理请求重新换取短 Token，不会输出 Token、
ticket 或 app key。

该脚本会真实递增配置版本并使已有 AI 助手 Cookie 失效，只允许在测试租户执行。若进程被强制
终止导致 `finally` 未运行，应立即在系统租户应用中心恢复输出中记录的原地址，再执行
`verify-ticket-rotation.mjs` 确认 SSO。
