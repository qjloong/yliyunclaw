# YliyunClaw AI 微站最终可执行落地方案、前后端任务清单与验收标准

> 版本：V1.6 Goal-Agent-Aligned Final  
> 日期：2026-08-01  
> 目标分支：`dev-v2`  
> 方案状态：已对照 Goal Agent V3.2 和当前项目代码完成审阅，可进入最终评审与 Sprint 0  
> 第一原则：**AI 自动完成建站，人工负责确认、补充和局部微调**  
> 架构原则：**会话优先、领域数据为事实源、页面辅助、模块隔离、默认关闭、渐进上线、公开渲染独立**

---

# 0. 文档目的

本文档是 AI 微站的最终实施基线，覆盖：

1. AI 自动建站产品闭环和 P0 边界；
2. 与 Goal Agent V3.2 共用的 Business App、配置、权限和治理体系；
3. MateClaw 当前 Agent、Conversation、MCP、Workspace、Tool Guard 的复用方式；
4. AI 微站领域模型、状态机、数据表、API、Agent Tools 和事件契约；
5. 独立 Public Projection、SSR Renderer、SEO、表单、线索和分析；
6. 前端 AI 助理、实时预览和人工微调编辑器；
7. 前后端详细任务、依赖、影响范围、测试与上线门禁。

本阶段不直接修改代码。先冻结本方案中的关键决策，再进入 Sprint 0 技术验证。

---

# 1. 最终产品原则

## 1.1 AI 自动建站优先

正确流程：

```text
业务资料 + 一句话目标
→ AI 自动理解资料
→ AI 自动规划站点
→ AI 自动生成完整网站
→ 用户确认或局部微调
→ 预览
→ 一键发布
```

禁止把 P0 做成传统低代码建站器：

```text
用户选模板
→ 用户建页面
→ 用户拖区块
→ 用户填文案
→ AI 只做润色
```

## 1.2 会话是核心入口

正式默认入口：

```text
/apps/website-agent/:siteId?/assistant
```

会话负责：

```text
创建站点
绑定资料
生成网站
查询进度
提出修改
确认 Proposal
发布和回滚
生成推广内容
```

页面负责：

```text
展示结构化数据
批量修改
局部人工微调
查看 Preview
查看发布历史、线索和分析
管理员诊断
```

## 1.3 领域数据库是事实源

聊天历史不是网站事实源。以下状态必须来自 AI 微站数据库：

```text
站点状态
资料绑定和快照
生成计划和步骤
页面、区块和内容实体
人工锁定字段
Proposal
Preview Revision
Published Revision
域名和路由
SEO
表单和线索
分析和推广内容
```

AI 回答“当前网站是什么状态”时必须先调用 Query Tool，不得根据历史消息猜测。

## 1.4 人工微调优先级最高

内容来源优先级：

```text
HUMAN_EDITED
> SOURCE_FACT
> AI_GENERATED
> SYSTEM_DEFAULT
```

人工锁定内容不得被后续 AI 自动覆盖。来源变化与人工内容冲突时，只生成合并 Proposal。

## 1.5 公开访问与后台生成解耦

已发布网站必须满足：

```text
不依赖账号登录
不实时调用 Agent
不实时调用 MCP
不实时读取云盘
不读取 Draft
不读取 MateClaw 用户和会话表
```

公开站点由独立 Renderer 读取 `wa_public_*` 投影并进行 SSR。

---

# 2. 对照 Goal Agent V3.2 的审阅结论

| Goal Agent 基线 | AI 微站最终结论 |
|---|---|
| 会话优先 | AI 建站助理是默认入口，编辑器为辅助 |
| 领域数据为事实源 | Site、Generation、Page、Proposal、Publish 均落业务库 |
| 第一方 Business App | 正式路由使用 `/apps/website-agent` |
| Business Runtime 共用 | 复用 `mateclaw-business-api/runtime`，不重复建设配置中心 |
| Port/Adapter | Website Domain 不依赖 Server 内部类 |
| 默认关闭 | 部署 Gate、平台、Workspace、Site、Capability 五层共同决定 |
| 独立 AutoConfiguration | Controller、Tool、Migration、Event Consumer 条件加载 |
| 独立 Flyway | Business Runtime 与 Website Agent 各自使用独立历史表 |
| Proposal + 确认 | 结构、内容、主题、SEO、表单和发布均使用 Proposal |
| 幂等和乐观锁 | 所有写操作支持 Idempotency Key 和 expectedVersion |
| Operation Log + Outbox | 数据库是事实源，SSE 只做通知 |
| Tool 风险分级 | R0 查询、R1 草稿、R2 低风险修改、R3 发布、R4 外链/删除 |
| 配置运行快照 | Generation Job 和 Publish 保存有效配置 Revision/Snapshot |
| 前端独立 Feature | `features/website-agent`，API 和 Store 不污染全局 |
| 最小化 ChatConsole 修改 | 仅增加一个通用 `hostRoutePath` 可选参数；无 Website 分支 |
| Feature Flag Off 门禁 | 关闭后普通 Chat、Agent、MCP、Cron、Teacher 不受影响 |

---

# 3. P0 产品范围

## 3.1 支持的站点类型

```text
企业产品介绍站
项目资料展示站
营销落地页
```

## 3.2 输入来源

```text
云盘单文件
云盘文件夹
MateClaw 知识库
人工补充文字
手工上传图片
```

云盘读写统一通过现有一粒云 MCP。

## 3.3 AI 自动生成结果

首次生成必须至少包括：

```text
站点名称和定位
目标受众和核心主张
站点类型和模板
导航和页面列表
页面区块和文案
主题 Token
图片和公开资产建议
SEO Title/Description/Slug/Alt
Canonical/Open Graph/JSON-LD
CTA 和表单
Preview Revision
发布检查结果
来源引用和风险提示
```

## 3.4 P0 不做

```text
任意 HTML/CSS/JavaScript 生成
完整低代码设计器
第三方模板市场
自定义代码注入
实时读取私有云盘渲染公开页面
无人值守代表用户调用云盘 MCP
自定义独立域名全自动接入
多语言站点
电商和在线支付
站内公开 AI 客服
```

---

# 4. MateClaw 现有能力复用矩阵

| 现有能力 | AI 微站使用方式 | 修改现有逻辑 |
|---|---|---:|
| JWT、Workspace | 身份、租户和 Capability | 仅新增 Capability |
| `ChatConsole` | AI 建站助理聊天主体 | 仅通用路由参数扩展 |
| `AgentService` | 模型执行和结构化流 | 否 |
| `ConversationService` | Site/User 会话 | 否 |
| `ToolRegistry` | 自动发现 Website Tool Bean | 否 |
| `AgentBindingService` | Website Assistant 工具白名单 | 否 |
| Tool Guard/审批 | 高风险 Tool 审批 | 否 |
| `McpClientManager` | 云盘工具调用 | 否 |
| OBO Identity | 用户身份转发 | 否 |
| Wiki | 知识库来源 | Adapter 复用 |
| Audit | 平台审计 Adapter | 否 |
| Notification | 线索和发布通知 Adapter | 否 |
| Caffeine | Renderer 本地缓存 | 复用依赖 |
| Pebble | SSR 模板渲染 | 复用依赖版本 |
| Flyway | 独立实例和独立历史表 | 不占核心版本 |
| Vue Router/MainLayout | Business Module Registry | 一次性稳定接入 |
| Pinia/HTTP | Feature-local Store/API | 不改全局行为 |

---

# 5. 路由、导航和模块定位

## 5.1 模块定位

```text
第一方业务应用 Business App
module_key = website-agent
```

不是：

```text
外置 JAR Plugin
单纯 Agent 模板
系统基础设置页面
云盘后台子模块
```

## 5.2 正式路由

```text
/apps/website-agent
/apps/website-agent/new
/apps/website-agent/settings
/apps/website-agent/:siteId/assistant
/apps/website-agent/:siteId/overview
/apps/website-agent/:siteId/sources
/apps/website-agent/:siteId/content
/apps/website-agent/:siteId/pages
/apps/website-agent/:siteId/seo
/apps/website-agent/:siteId/forms
/apps/website-agent/:siteId/leads
/apps/website-agent/:siteId/analytics
/apps/website-agent/:siteId/promotion
/apps/website-agent/:siteId/domains
/apps/website-agent/:siteId/settings
```

平台治理：

```text
/settings/modules
/settings/modules/website-agent
```

兼容路由：

```text
/website-agent/*
→ 前端 redirect 到 /apps/website-agent/*
```

## 5.3 主导航

```text
核心
├── Dashboard
├── Chat
├── Agents
├── Wiki
└── Memory

业务应用
├── Goal Agent
├── AI 微站
└── Future Business Apps

连接与扩展
├── Channels
├── Skills
├── Plugins
└── Activity

系统
├── Settings
├── Security
└── Docs
```

Settings 左侧只增加“业务模块”，不为每个业务应用增加专属菜单。

---

# 6. 后端模块架构

## 6.1 Maven 模块

```text
mateclaw-plugin-api
mateclaw-business-api
mateclaw-business-runtime
mateclaw-website-public-api
mateclaw-website-agent
mateclaw-site-renderer
mateclaw-server
mateclaw-plugin-sample
mateclaw-plugin-search-sample
```

## 6.2 依赖方向

```text
mateclaw-business-api
      ▲
      │
mateclaw-business-runtime
      ▲
      │
mateclaw-website-agent ─────► mateclaw-website-public-api
      ▲                                ▲
      │                                │
mateclaw-server               mateclaw-site-renderer
```

禁止：

```text
website-agent → mateclaw-server
site-renderer → mateclaw-server
site-renderer → website-agent
business-runtime → website-agent domain
```

## 6.3 `mateclaw-business-api`

稳定契约：

```text
BusinessModuleDescriptor
BusinessModuleProvider
BusinessModuleFeature
BusinessModuleScope
BusinessModuleRuntimeState
BusinessModuleConfigSchema
BusinessModuleConfigValidator
BusinessModuleImpactAnalyzer
BusinessModuleHealthProvider
BusinessModuleLifecycleListener
```

## 6.4 `mateclaw-business-runtime`

通用能力：

```text
模块注册和清单
平台/Workspace 启停
Runtime State
有效配置解析
配置草稿、校验和影响预览
Revision 发布和回滚
配置应用记录
缓存失效
模块健康
通用模块 API
Operation Log
Outbox
```

## 6.5 `mateclaw-website-public-api`

仅提供稳定公开契约：

```text
PublishedSiteSnapshot
PublishedRouteSnapshot
PublishedPageSnapshot
PublishedDatasetSnapshot
PublishedAssetDescriptor
PublishedFormDescriptor
RenderBlock
SeoMetadata
ThemeTokens
```

不得依赖 Spring MVC、MyBatis 或 Server。

## 6.6 `mateclaw-website-agent`

目录建议：

```text
vip.mate.website
├── autoconfigure
├── web
├── application
├── domain
├── infrastructure
├── generation
├── source
├── publish
├── agent
├── port
├── event
└── common
```

负责：

```text
站点领域数据
生成流水线
来源和快照
Page DSL
Proposal
Preview/Publish
SEO
表单、线索、分析和推广
Website Agent Tools
```

## 6.7 `mateclaw-server` Bridge

```text
vip.mate.website.bridge
├── agent
├── conversation
├── mcp
├── workspace
├── audit
├── notification
├── knowledge
└── tool
```

只负责适配，不放业务规则。

## 6.8 `mateclaw-site-renderer`

独立 Spring Boot 应用：

```text
Host Resolve
Route Resolve
SSR
SEO
Public Asset
Sitemap/robots
Public Form
Analytics Inbox
Cache
Security Headers
```

---

# 7. 自动配置、启用逻辑和配置治理

## 7.1 条件自动配置

```java
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "mateclaw.website-agent",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class WebsiteAgentAutoConfiguration {
}
```

默认：

```yaml
mateclaw:
  website-agent:
    enabled: false
```

关闭时：

```text
不注册 Controller
不注册 Website Tool
不运行 Website Flyway
不启动 Generation Worker
不启动 Outbox Dispatcher
不显示前端入口
wa_* 数据保留
普通 Chat/Agent/MCP/Cron 不受影响
```

## 7.2 五层有效性

```text
WebsiteAgentAvailable =
    deploymentGate
    && platformRuntimeEnabled
    && workspaceEnabled
    && siteEnabled
    && userHasCapability
```

运行状态：

```text
ACTIVE
READ_ONLY
DRAINING
DISABLED
```

## 7.3 配置作用域

```text
PLATFORM
WORKSPACE
RESOURCE(SITE)
```

优先级：

```text
Site > Workspace > Platform Default
Platform Hard Limit 始终最高
```

## 7.4 配置生效分类

### A 即时生效

```text
站点暂停
公开表单开关
分析开关
通知开关
限流
站点可见性
```

### B 下一次请求或下一次生成生效

```text
生成模型
Token 预算
来源文件上限
并发和超时
AI Prompt 版本
允许 Block
```

`wa_generation_job` 保存：

```text
business_config_revision_id
config_snapshot_json
```

### C 需要重新发布

```text
主题
SEO 默认策略
模板
公共数据绑定
表单结构
```

### D 需要重启

```text
部署 Gate
数据源
独立 Flyway
加密主密钥
Renderer 部署方式
```

## 7.5 配置页面

```text
/apps/website-agent/settings                Workspace 配置
/apps/website-agent/:siteId/settings        Site 配置
/settings/modules/website-agent             Platform 配置
```

---

# 8. 独立 Flyway 和回滚

## 8.1 独立历史表

Business Runtime：

```text
flyway_schema_history_business_runtime
classpath:db/business-runtime/{h2|mysql|kingbase}
```

Website Agent：

```text
flyway_schema_history_website_agent
classpath:db/website-agent/{h2|mysql|kingbase}
```

Renderer 公共表由 Website Agent 迁移管理，不单独建第三套历史。

## 8.2 实施前 Spike

必须验证：

```text
依赖 JAR AutoConfiguration 加载
独立 Mapper 扫描
两个独立 Flyway 的执行顺序
Feature Flag 关闭时不执行迁移
H2/MySQL/Kingbase SQL 一致性
Website 迁移失败可以通过关闭模块隔离
```

## 8.3 回滚

```text
代码回滚：mateclaw.website-agent.enabled=false
数据保留：不执行 destructive down migration
配置回滚：发布旧快照的新 Revision
站点回滚：创建新的 rollback publish revision
```

---

# 9. 状态机

## 9.1 Site

```text
DRAFT
→ GENERATING
→ WAITING_REVIEW
→ READY
→ PUBLISHED
→ PAUSED
→ ARCHIVED

非终态 → FAILED
```

## 9.2 Generation Job

```text
CREATED
→ READING_SOURCES
→ ANALYZING
→ PLANNING
→ GENERATING_STRUCTURE
→ GENERATING_PAGES
→ GENERATING_SEO
→ GENERATING_FORM
→ BUILDING_PREVIEW
→ RUNNING_CHECKS
→ WAITING_REVIEW
→ READY_TO_PUBLISH

任意运行态 → FAILED / CANCELLED
```

## 9.3 Generation Step

```text
PENDING → RUNNING → SUCCEEDED
                  → FAILED
                  → SKIPPED
```

失败步骤可单独重试，成功步骤按 input hash 复用。

## 9.4 Proposal

```text
DRAFT
→ CONFIRMED
→ APPLYING
→ APPLIED

DRAFT → REJECTED / EXPIRED
CONFIRMED/APPLYING → FAILED
```

## 9.5 Publish

```text
PREPARING
→ VALIDATING
→ PROJECTING
→ WARMING
→ SWITCHING
→ PUBLISHED

任意阶段 → FAILED
```

发布失败时旧版本继续服务。

## 9.6 Domain 和 Form

```text
Domain: PENDING → VERIFYING → ACTIVE → ERROR → DISABLED
Form:   DRAFT → ACTIVE → PAUSED → ARCHIVED
Lead:   NEW → CONTACTED → QUALIFIED → CONVERTED / CLOSED
```

---

# 10. 数据模型

## 10.1 通用字段

除关系表外统一包含：

```text
id BIGINT
workspace_id BIGINT
site_id BIGINT
version INT
deleted TINYINT
created_by VARCHAR
updated_by VARCHAR
create_time DATETIME/TIMESTAMP
update_time DATETIME/TIMESTAMP
```

规则：

```text
Snowflake ID
API Long ID 全程使用字符串
JSON 使用 TEXT/CLOB
不向 MateClaw 核心表建立物理外键
写操作带 expectedVersion
关键数据逻辑删除
```

## 10.2 P0 领域表

### Site 和会话

```text
wa_site
wa_site_member
wa_site_conversation
wa_site_setting
wa_site_domain
wa_site_redirect
```

### 生成和 Proposal

```text
wa_generation_plan
wa_generation_job
wa_generation_step
wa_proposal
wa_operation_log
wa_outbox_event
```

### 页面和内容

```text
wa_site_revision
wa_page
wa_page_revision
wa_page_block
wa_menu
wa_content_entity
wa_content_field
wa_collection
wa_collection_item
```

### 来源和资产

```text
wa_source_binding
wa_source_item
wa_source_snapshot
wa_source_diff
wa_source_operation
wa_asset
wa_asset_variant
wa_asset_source_link
```

### 模板、主题和 SEO

```text
wa_template
wa_template_version
wa_theme
wa_theme_revision
wa_seo_setting
wa_seo_audit
wa_structured_data
```

### 发布和公共投影

```text
wa_publish
wa_public_site
wa_public_domain
wa_public_route
wa_public_page
wa_public_dataset
wa_public_dataset_item
wa_public_asset
wa_public_form
```

### 转化和推广

```text
wa_form
wa_form_field
wa_form_submission_inbox
wa_lead
wa_lead_activity
wa_analytics_event_inbox
wa_analytics_daily
wa_analytics_page_daily
wa_analytics_source_daily
wa_promotion_batch
wa_promotion_content
wa_share_link
```

## 10.3 关键快照字段

`wa_generation_job`：

```text
business_config_revision_id
config_snapshot_json
source_snapshot_set_id
prompt_version
model_provider
model_name
prompt_tokens
completion_tokens
```

`wa_publish`：

```text
source_revision_id
public_revision_no
business_config_revision_id
config_snapshot_json
route_manifest_json
seo_snapshot_json
status
published_by
published_at
```

页面和字段：

```text
content_origin
last_generated_job_id
last_source_snapshot_id
human_locked
last_modified_by
last_modified_at
```

---

# 11. 会话上下文、Agent 和 Tool

## 11.1 上下文解析

每个 Website Tool 从可信运行上下文解析：

```text
conversationId
workspaceId
userId/username
agentId
siteId（由 wa_site_conversation 解析）
```

解析优先级：

```text
1. conversationId 已绑定 Site
2. Website Host 显式绑定的 Site Context
3. 当前用户最近活跃 Site
4. 无 Site 时要求创建或选择
```

模型传入的 `workspaceId/siteId` 只能作为提示，不能作为授权依据。

## 11.2 Assistant 模型

```text
每个 Workspace 一个默认 Website Assistant Agent
多个 Site 共享 Agent 定义
每个用户每个 Site 独立 Conversation
Site 上下文由绑定表保证
不为每个 Site 自动复制 Agent
```

## 11.3 系统 Prompt 强制规则

```text
每次开始先调用 wa_get_context
不得根据聊天历史猜测站点状态
所有结构性写操作先产生 Proposal
发布、回滚、删除和公开附件必须确认
不得信任模型传入的 Site ID
不得把推断当成企业事实
不得公开私有云盘 URL
不得生成任意脚本、iframe 或外部表单 Action
权限错误后不得尝试其他 Workspace/Site
```

## 11.4 Tool 分组

### 查询 R0

```text
wa_get_context
wa_get_site_overview
wa_get_generation_status
wa_list_sources
wa_list_pages
wa_get_page
wa_get_seo_audit
wa_get_publish_check
wa_get_analytics_summary
wa_list_leads
```

### 草稿 R1

```text
wa_create_generation_plan
wa_draft_page_change
wa_draft_structure_change
wa_draft_theme_change
wa_draft_seo_change
wa_draft_form_change
wa_regenerate_section
wa_generate_promotion
```

### 低风险命令 R2

```text
wa_update_manual_content
wa_lock_content
wa_refresh_preview
wa_check_source_updates
```

### 高风险 R3

```text
wa_confirm_proposal
wa_start_generation
wa_publish_site
wa_rollback_site
wa_apply_source_update
```

### 强制审批 R4

```text
wa_archive_site
wa_remove_published_page
wa_publish_private_asset
wa_create_public_share_link
wa_change_legal_or_price_content
```

## 11.5 Tool 返回规范

```json
{
  "success": true,
  "code": "WA_OK",
  "message": "Proposal created",
  "dataVersion": 12,
  "generatedAt": "2026-08-01T08:00:00Z",
  "siteId": "193...",
  "proposalId": "194...",
  "data": {},
  "warnings": [],
  "nextActions": [
    {"action": "CONFIRM_PROPOSAL", "label": "确认应用"}
  ]
}
```

---

# 12. AI 自动建站流水线

## 12.1 确定性编排

```text
ReadSourcesStep
→ BuildInventoryStep
→ AnalyzeFactsStep
→ DetectMissingAndConflictStep
→ SelectSiteTypeStep
→ SelectTemplateStep
→ PlanThemeStep
→ PlanStructureStep
→ GeneratePagesStep
→ GenerateSeoStep
→ GenerateFormStep
→ BuildPreviewStep
→ RunPublishCheckStep
```

不允许把完整流程交给一个自由 Prompt 决定。

## 12.2 Source Inventory

每个来源项保存：

```text
sourceRef
path/name
mimeType
size
modifiedAt
permissionState
contentHash
extractStatus
publicCandidate
sensitiveFlags
```

## 12.3 Fact 分类

```text
SOURCE_FACT
AI_INFERENCE
MISSING
CONFLICT
SENSITIVE
USER_CONFIRMED
```

价格、资质、联系方式、客户名称、法律声明和项目数据必须有明确来源或用户确认。

## 12.4 Page DSL

AI 只生成结构化 JSON DSL：

```json
{
  "route": "/products",
  "title": "产品中心",
  "seo": {"title": "产品中心 - 企业名称"},
  "sections": [
    {"type": "hero", "props": {"headline": "专业产品与解决方案"}},
    {"type": "product_grid", "binding": {"datasetKey": "products"}}
  ]
}
```

发布前校验：

```text
JSON Schema
Block Allowlist
字段长度
URL Scheme
数据绑定
资产存在性
SEO
表单
XSS Sanitizer
```

## 12.5 MVP Block

```text
header footer hero rich_text image image_text gallery
feature_grid product_grid product_detail case_grid case_detail
project_overview document_list file_download article_list article_detail
faq timeline team contact cta lead_form statistics logo_wall testimonial
```

## 12.6 模板

```text
企业产品介绍站 × 1
项目资料展示站 × 1
营销落地页 × 2
```

## 12.7 重试和恢复

```text
步骤级 attempt
input_hash
output_json
error_code
最长 2 次 AI 修复重试
失败后仅重跑失败步骤和后续步骤
取消后保留已生成 Draft
```

---

# 13. MCP 来源与身份边界

## 13.1 调用链

```text
Website Application Service
→ WebsiteCloudMcpPort
→ YliyunMcpSourceAdapter
→ McpClientManager.callTool
```

不新增第二套 MCP Client。

## 13.2 P0 工具

```text
file.list
file.search
file.read
file.create
file.save
```

## 13.3 默认限制

```text
目录深度 3
最大文件数 100
单文件 50MB
单次总量 500MB
并发读取 2
任务最长 10 分钟
```

超限时：

```text
返回 PARTIAL
列出跳过项
允许用户选择重点资料
继续生成可用网站
```

## 13.4 后台同步限制

现有 OBO 对 cron/system 来源 fail-closed，因此 P0 仅支持：

```text
用户手工重新读取
用户手工检查更新
用户确认应用 Diff
```

P1 完成可撤销 Delegation 后再支持无人值守同步。

---

# 14. 人工微调、Proposal 和合并

## 14.1 会话修改

```text
用户自然语言修改
→ 查询最新 Draft Revision
→ 定位页面/区块/字段
→ 生成 Diff Proposal
→ Preview 变化
→ 用户确认
→ 原子生成新 Draft Revision
```

## 14.2 可视化微调

P0 支持：

```text
修改文字
替换图片
调整页面顺序
调整区块顺序
修改主题 Token
修改按钮和 CTA
修改 SEO
修改表单字段
增加或归档页面
```

不支持任意画布自由布局。

## 14.3 Proposal 类型

```text
SITE_SETUP
SOURCE_BINDING
SITE_STRUCTURE
PAGE_CONTENT
THEME_CHANGE
SEO_CHANGE
FORM_CHANGE
SOURCE_SYNC
PUBLISH
ROLLBACK
PROMOTION_BATCH
```

## 14.4 幂等和乐观锁

写请求统一支持：

```text
X-Idempotency-Key
If-Match / expectedVersion
```

冲突：

```text
409 WA_VERSION_CONFLICT
```

## 14.5 人工锁定合并

来源更新时：

```text
未人工修改字段 → 可生成自动更新建议
人工锁定字段 → 不覆盖
来源删除 → 默认不删除已发布内容，只标记风险
冲突字段 → Proposal 中逐项选择
```

---

# 15. 发布、Public Projection 和 Renderer

## 15.1 发布流程

```text
Publish Check
→ 生成 Public Projection
→ 复制 Public Asset
→ Renderer 预热
→ SSR Smoke Test
→ 原子切换 Public Revision Pointer
→ 缓存失效
→ 发布事件
```

## 15.2 Public Projection 边界

Renderer 只读：

```text
wa_public_site
wa_public_domain
wa_public_route
wa_public_page
wa_public_dataset
wa_public_dataset_item
wa_public_asset
wa_public_form
```

Renderer 写入：

```text
wa_form_submission_inbox
wa_analytics_event_inbox
```

建议使用独立数据库账号：

```text
public_read_user
public_inbox_write_user
```

## 15.3 SSR

P0 使用：

```text
Pebble
自动 HTML Escape
Block Renderer Registry
Theme Token
Caffeine Cache
```

初始 HTML 必须包含正文，不做爬虫专用内容分支。

## 15.4 公开访问模式

```text
DRAFT
PUBLIC
UNLISTED
PAUSED
ARCHIVED
```

`UNLISTED` 不依赖账号，但使用 `noindex`。

## 15.5 SEO

自动生成和检查：

```text
Title
Meta Description
Canonical
Open Graph
Heading
Alt
Breadcrumb
Last-Modified
Sitemap
robots.txt
JSON-LD
301 Redirect
404/410/429/503 状态码
```

## 15.6 域名

P0：

```text
{siteSlug}.sites.example.com
Wildcard DNS + TLS
```

P1：自定义独立域名。

---

# 16. 表单、线索、分析和推广

## 16.1 表单

AI 根据目标推荐：

```text
咨询
预约演示
申请试用
获取报价
项目联系
资料下载
```

公开提交保护：

```text
服务端校验
Honeypot
最短填写时间
IP/Site 限流
重复检测
可选验证码
```

## 16.2 Lead

保存：

```text
landingPage
referrer
utm_source
utm_medium
utm_campaign
formId
sourceRevision
```

## 16.3 第一方分析

事件：

```text
page_view
form_view
form_submit
cta_click
download
outbound_click
```

Raw Event 短期保留，日报长期保存。

## 16.4 推广内容

生成：

```text
微信公众号文章
朋友圈文案
短视频脚本
海报文案
SEO 关键词
邮件文案
带 UTM 分享链接
```

推广内容必须关联 Published Revision，站点更新后标记为可能过期。

---

# 17. 事件、Outbox 和实时同步

## 17.1 Outbox

业务事务内同时写：

```text
wa_operation_log
wa_outbox_event
```

事件至少一次投递，消费方幂等。

## 17.2 事件类型

```text
site.created
site.updated
source.bound
source.snapshot.created
generation.started
generation.step.updated
generation.completed
generation.failed
proposal.created
proposal.applied
preview.updated
publish.started
publish.completed
publish.failed
site.paused
lead.created
analytics.aggregated
promotion.generated
```

## 17.3 前端 SSE

```text
GET /api/v1/website-agent/sites/{siteId}/events?lastEventId=...
```

规则：

```text
SSE 只传事件摘要
数据库是事实源
版本不连续时重拉 Overview
断开指数退避
30 秒后降级轮询
```

---

# 18. 前端架构和页面

## 18.1 目录

```text
mateclaw-ui/src/features/website-agent/
├── manifest.ts
├── routes.ts
├── api/
├── pages/
├── components/
│   ├── assistant/
│   ├── generation/
│   ├── proposal/
│   ├── source/
│   ├── preview/
│   ├── editor/
│   ├── seo/
│   ├── form/
│   ├── analytics/
│   └── promotion/
├── composables/
├── stores/
├── schemas/
├── styles/
└── types/
```

## 18.2 Business Module Registry

```text
src/business-modules/
├── types.ts
├── registry.ts
├── route-builder.ts
├── useBusinessModules.ts
├── BusinessAppsHome.vue
├── BusinessModulesSettings.vue
└── BusinessModuleStatusGuard.vue
```

运行时显示条件：

```text
前端已编译
AND 后端已注册
AND 平台已启用
AND Workspace 已启用
AND 用户有 view:website-agent
```

## 18.3 Assistant 页面

```text
┌──────────────────────────────────────────────────────────────┐
│ Site Selector | 状态 | AI 自动模式 | Preview | Publish      │
├───────────────────────────────┬──────────────────────────────┤
│ Embedded ChatConsole          │ Live SSR Preview             │
│                               │ Device / Revision / URL       │
│                               ├──────────────────────────────┤
│                               │ Generation / Proposal / Risk │
├───────────────────────────────┴──────────────────────────────┤
│ 可折叠：来源、操作历史、发布历史                              │
└──────────────────────────────────────────────────────────────┘
```

## 18.4 ChatConsole 最小修改

当前 `ChatConsole` 只在路由为 `/chat` 或 `/embed/cloud-agent` 时恢复和同步 Agent/Conversation。为了在 Business App 内直接复用，增加一个通用可选参数：

```ts
hostRoutePath?: string
```

唯一行为变化：

```ts
const chatRoutePath = computed(() =>
  props.hostRoutePath || (props.embedded ? '/embed/cloud-agent' : '/chat')
)
```

Website Host 使用：

```vue
<ChatConsole
  embedded
  :host-route-path="route.path"
/>
```

Agent 和 Conversation 继续通过 Route Query 传入。

禁止加入：

```text
Website API
siteId 业务判断
Proposal/Preview Store
Website CSS 分支
Website 专用消息类型
```

外层页面通过局部 CSS 隐藏不需要的模型选择和会话按钮；若该方式不稳定，再单独提取通用 `ChatSurface`，不得复制 ChatConsole。

## 18.5 页面复用原则

```text
直接满足 → 复用
只需外围适配 → Wrapper
内部行为变化 → adapted/
成熟且通用 → 单独 PR 提取 shared
```

禁止：

```text
给全局 Store 添加 Website 状态
向 api/index.ts 堆叠 Website API
修改全局 .card/.sidebar/.btn
复制 useChat 或 MessageList
```

---

# 19. Capability 和安全

## 19.1 Capability 类型

前端从固定枚举调整为：

```ts
type CoreCapability = 'chat' | 'view:wiki' | 'manage:agents' | ...
type BusinessCapability =
  | `view:${string}`
  | `manage:${string}`
  | `admin:${string}`
export type Capability = CoreCapability | BusinessCapability
```

AI 微站：

```text
view:website-agent
manage:website-agent
admin:website-agent
```

## 19.2 Site 角色

```text
OWNER
MANAGER
EDITOR
ANALYST
VIEWER
```

## 19.3 写操作强制链

```text
JWT
→ Module Gate
→ Workspace Membership
→ Capability
→ Site Membership
→ Resource Workspace/Site
→ Version
→ Idempotency
→ Business State
→ Tool Risk Policy
```

## 19.4 公开安全

```text
Host 白名单
CSP
安全 Header
HTML Escape
URL Allowlist
无任意 JS
无私有云盘 URL
表单限流
真实 HTTP 状态码
Renderer 最小数据库权限
```

---

# 20. 影响范围评估

## 20.1 现有后端文件

必须修改：

```text
pom.xml
mateclaw-server/pom.xml
Capability.java
RoleCapabilities.java
```

可能修改：

```text
application.yml：仅增加 deployment gate 默认值
```

不修改：

```text
MateClawApplication
SecurityConfig
AgentService
AgentGraphBuilder
ToolRegistry
AgentBindingService
McpClientManager
McpIdentityForwardService
ConversationService
CronJobService
```

## 20.2 现有前端文件

修改：

```text
router/index.ts：一次接入 Business Route Builder
MainLayout.vue：一次接入 Apps 导航
capabilities.ts：支持 Business Capability 模板类型
ChatConsole.vue：仅 hostRoutePath 可选参数
zh-CN.ts / en-US.ts：模块文案
```

不修改：

```text
useChat
MessageList
ChatInputWorkspaceBar
ConversationSidebar
useWorkspaceStore
api/index.ts
```

## 20.3 数据库

新增：

```text
mate_business_*
wa_*
```

不修改 MateClaw 核心业务表结构。只在 Agent/Tool Binding 表写入 Website Assistant 初始化数据。

## 20.4 部署

新增：

```text
site-renderer 进程/容器
Wildcard Domain 和 TLS
Public Asset 存储
Renderer DB 用户
Nginx 路由
```

---

# 21. Definition of Done

每项 P0 任务必须满足：

```text
代码 Review
单元测试
集成测试
API/Schema 文档
Feature Flag Off 测试
跨 Workspace/Site 隔离测试
日志和指标
无高危静态扫描问题
可回滚
不降低现有 MateClaw 回归通过率
```

---

# 22. 后端详细任务清单

## Epic A 方案冻结与技术 Spike

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| A-01 | 冻结产品词典、P0/非范围 | 领域词典和范围表 | 无 | 1d |
| A-02 | 冻结 Site/Generation/Proposal/Publish 状态机 | 状态图和错误码 | A-01 | 1d |
| A-03 | 冻结 Capability 和 Site 角色 | 权限矩阵 | A-01 | 1d |
| A-04 | 验证 Business/Website AutoConfiguration | 可运行 Demo | 无 | 1d |
| A-05 | 验证依赖模块 Mapper 扫描 | CRUD Test | A-04 | 1d |
| A-06 | 验证双独立 Flyway | H2/MySQL Demo | A-04 | 2d |
| A-07 | 验证 Agent 结构化输出调用链 | JSON Schema Spike | 无 | 2d |
| A-08 | 验证 McpClientManager OBO 调用 | file.list/read Spike | 无 | 2d |
| A-09 | 验证 ChatConsole hostRoutePath | 前端 Demo | 无 | 1d |
| A-10 | 验证 Pebble SSR 和公开数据库权限 | Renderer Demo | 无 | 2d |
| A-11 | 输出 ADR-001~008 | 架构决策 | A-04~10 | 2d |

## Epic B 通用 Business Runtime

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| B-01 | 新增 mateclaw-business-api | Maven 模块 | A-11 | 1d |
| B-02 | 定义 Descriptor/Provider/Feature/Scope | Java API | B-01 | 2d |
| B-03 | 新增 mateclaw-business-runtime | Maven 模块 | B-01 | 1d |
| B-04 | 模块 Provider 注册中心 | Registry | B-02/03 | 2d |
| B-05 | mate_business_module 表 | Migration/Mapper | A-06 | 2d |
| B-06 | Config/Revision/Application 表 | Migration/Mapper | A-06 | 4d |
| B-07 | Effective Config Resolver | 三级继承 | B-06 | 4d |
| B-08 | Draft/Validate/Impact/Publish | 配置服务 | B-07 | 5d |
| B-09 | Revision Rollback | 回滚服务 | B-08 | 2d |
| B-10 | Runtime Gate | ACTIVE/READ_ONLY/DRAINING | B-05/07 | 3d |
| B-11 | 配置缓存和 Outbox 失效 | 多节点一致性 | B-08 | 3d |
| B-12 | 模块健康聚合 | Health Provider | B-04 | 2d |
| B-13 | 通用 Module REST API | 清单/状态/配置 | B-04~12 | 3d |
| B-14 | Operation Log | 启停/配置审计 | B-08~10 | 2d |
| B-15 | ArchUnit 约束 | 架构测试 | B-01~14 | 2d |
| B-16 | H2/MySQL 集成测试 | 测试证据 | B-01~15 | 4d |

## Epic C Website 模块骨架

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| C-01 | 新增 website-public-api | 公开 DTO | A-11 | 1d |
| C-02 | 新增 website-agent | Maven 模块 | A-11 | 1d |
| C-03 | 新增 site-renderer | 独立 Boot 模块 | A-10 | 2d |
| C-04 | Website Module Provider | Descriptor/Health | B-02 | 2d |
| C-05 | Website Config Schema/Defaults | 配置定义 | B-02 | 2d |
| C-06 | Website Config Validator | 上限和组合校验 | C-05 | 3d |
| C-07 | Website Impact Analyzer | Site/Publish 影响 | C-06/B-08 | 3d |
| C-08 | Website AutoConfiguration | 条件加载 | A-04 | 2d |
| C-09 | 独立 Flyway Bean | 三数据库位置 | A-06 | 2d |
| C-10 | 统一 WA 错误码和响应 | WA_* | C-02 | 2d |
| C-11 | Actor/Site Context Resolver | 可信上下文 | C-02 | 3d |
| C-12 | 幂等组件 | 写请求防重 | C-02 | 2d |
| C-13 | Version Guard | 乐观锁 | C-02 | 2d |
| C-14 | Website Operation/Outbox 基础 | 事件管道 | C-02 | 3d |

## Epic D 领域和数据库

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| D-01 | Site/Member/Conversation/Setting | 实体/Mapper/状态机 | C-09 | 3d |
| D-02 | Domain/Redirect | 实体/Mapper | C-09 | 2d |
| D-03 | Generation Plan/Job/Step | 实体/Mapper | C-09 | 4d |
| D-04 | Proposal | 实体/Mapper | C-09 | 2d |
| D-05 | Site/Page/Block/Menu Revision | 实体/Mapper | C-09 | 5d |
| D-06 | Content Entity/Field/Collection | 实体/Mapper | C-09 | 4d |
| D-07 | Source Binding/Item/Snapshot/Diff | 实体/Mapper | C-09 | 4d |
| D-08 | Asset/Variant/Source Link | 实体/Mapper | C-09 | 3d |
| D-09 | Template/Theme | 实体/Mapper | C-09 | 3d |
| D-10 | SEO/Structured Data/Audit | 实体/Mapper | C-09 | 3d |
| D-11 | Form/Lead | 实体/Mapper | C-09 | 3d |
| D-12 | Analytics/Promotion | 实体/Mapper | C-09 | 3d |
| D-13 | Publish/Public Projection | 实体/Mapper | C-09 | 4d |
| D-14 | Operation/Outbox | 实体/Mapper | C-14 | 2d |
| D-15 | H2 迁移 | 完整 SQL | D-01~14 | 3d |
| D-16 | MySQL 迁移 | 完整 SQL | D-01~14 | 3d |
| D-17 | Kingbase 迁移 | 完整 SQL | D-01~14 | 3d |
| D-18 | 索引和 10k Page 数据测试 | 性能证据 | D-15~17 | 3d |

## Epic E Server Bridge 和 REST

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| E-01 | Workspace Access Adapter | 复用 WorkspaceService | C-11 | 2d |
| E-02 | Audit/Notification Adapter | 复用现有服务 | C-14 | 2d |
| E-03 | Knowledge Adapter | 复用 Wiki | C-02 | 2d |
| E-04 | Site Controller | CRUD/状态 | D-01 | 3d |
| E-05 | Generation Controller | 启动/取消/重试/SSE | D-03 | 4d |
| E-06 | Source Controller | 绑定/读取/检查更新 | D-07 | 3d |
| E-07 | Page/Content Controller | Revision/锁定 | D-05/06 | 4d |
| E-08 | Proposal Controller | 查询/确认/拒绝 | D-04 | 3d |
| E-09 | Preview/Publish Controller | 检查/发布/回滚 | D-13 | 4d |
| E-10 | SEO/Form/Lead API | 业务 API | D-10/11 | 4d |
| E-11 | Analytics/Promotion API | 业务 API | D-12 | 3d |
| E-12 | Domain API | 二级域名 | D-02 | 2d |
| E-13 | Website Exception Advice | R<T> 映射 | C-10 | 2d |
| E-14 | Controller Contract Test | 权限/版本/幂等 | E-04~13 | 5d |

## Epic F Conversation、Agent 和 Tools

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| F-01 | WebsiteConversationPort | 稳定接口 | C-02 | 1d |
| F-02 | Conversation Adapter | ConversationService | F-01 | 2d |
| F-03 | Site/User Conversation Binding | 唯一约束 | D-01/F-02 | 2d |
| F-04 | WebsiteAgentRuntimePort | 稳定接口 | C-02 | 1d |
| F-05 | Agent Runtime Adapter | AgentService | F-04 | 3d |
| F-06 | Structured Output Validator/Repair | JSON Schema | A-07/F-05 | 4d |
| F-07 | website-assistant.json | 内置 Agent 模板 | F-05 | 2d |
| F-08 | Workspace Assistant Provisioning | 共享 Agent | F-07 | 2d |
| F-09 | Tool Context Resolver | conversation→site | C-11/F-03 | 2d |
| F-10 | R0 Query Tools | 10 个 Tool | F-09 | 4d |
| F-11 | R1 Draft Tools | 8 个 Tool | F-09 | 4d |
| F-12 | R2/R3/R4 Command Tools | 命令和审批 | F-09 | 5d |
| F-13 | Tool 风险和审计 | Tool Guard/Operation | F-10~12 | 3d |
| F-14 | Tool 返回规范测试 | WA envelope | F-10~13 | 2d |
| F-15 | 会话建站 E2E | 完整脚本 | F-01~14 | 4d |

## Epic G MCP 和来源

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| G-01 | WebsiteCloudMcpPort | DTO/Port | C-02 | 2d |
| G-02 | Yliyun ToolContext Factory | ChatOrigin.web | G-01 | 2d |
| G-03 | file.list Adapter | 目录扫描 | G-02 | 2d |
| G-04 | file.search Adapter | 搜索 | G-02 | 2d |
| G-05 | file.read Adapter | 读取和解析响应 | G-02 | 3d |
| G-06 | file.create Adapter | 回存内容 | G-02 | 2d |
| G-07 | file.save Adapter | 保存版本 | G-02 | 2d |
| G-08 | ToolCallResult Error Mapper | 错误分层 | G-03~07 | 2d |
| G-09 | Trace ID 贯通 | MateClaw/MCP/Cloud | G-02 | 1d |
| G-10 | Depth/Count/Size Guard | PARTIAL 策略 | G-03~05 | 2d |
| G-11 | Source Inventory Builder | 清单和 hash | D-07/G-03~05 | 3d |
| G-12 | Source Snapshot/Diff | 变化检测 | D-07/G-11 | 4d |
| G-13 | OBO 越权测试 | 跨租户 | G-02~07 | 3d |
| G-14 | MCP 不可用降级 | 已发布站点不受影响 | G-01~12 | 3d |

## Epic H 自动建站流水线

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| H-01 | Generation Orchestrator | 固定步骤编排 | D-03 | 4d |
| H-02 | ReadSourcesStep | 来源读取 | G-11/H-01 | 3d |
| H-03 | BuildInventoryStep | 内容库存 | H-02 | 3d |
| H-04 | AnalyzeFactsStep | 事实/推断/敏感 | F-06/H-03 | 4d |
| H-05 | Missing/Conflict Step | 缺失和冲突 | H-04 | 3d |
| H-06 | Site Type Selector | 三类站点 | H-04 | 2d |
| H-07 | Template Selector | 模板匹配 | H-06 | 2d |
| H-08 | Theme Planner | Theme Token | H-07 | 3d |
| H-09 | Structure Planner | 导航和页面 | H-06~08 | 4d |
| H-10 | Page DSL Schema/Registry | 契约 | C-01 | 4d |
| H-11 | Page Generator | 页面生成 | H-09/10 | 6d |
| H-12 | SEO Generator | SEO/JSON-LD | H-11 | 3d |
| H-13 | Form/CTA Generator | 转化 | H-11 | 3d |
| H-14 | Preview Builder | Preview Revision | H-11~13 | 4d |
| H-15 | Publish Check | PASS/WARN/BLOCK | H-14 | 4d |
| H-16 | Step Resume/Retry/Cancel | 恢复 | H-01~15 | 4d |
| H-17 | Generation Metrics/Budget | Token/耗时 | H-01~16 | 2d |
| H-18 | 三类站点 E2E | 成功率证据 | H-01~17 | 6d |

## Epic I Proposal 和人工微调

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| I-01 | Content Origin Model | 来源优先级 | D-06 | 2d |
| I-02 | Human Lock | 锁定保护 | D-06 | 2d |
| I-03 | Page/Block/Field Diff | 可读 Diff | D-04~06 | 4d |
| I-04 | Proposal Draft/Patch | 编辑草稿 | D-04 | 3d |
| I-05 | Proposal Materializer | 事务应用 | I-03/04 | 4d |
| I-06 | Version Conflict Handler | 409 | C-13/I-05 | 2d |
| I-07 | Manual Content Update | 人工编辑 | I-01/02 | 3d |
| I-08 | Page/Block Reorder | 结构微调 | D-05 | 3d |
| I-09 | Theme/SEO/Form Proposal | 局部修改 | D-09~11 | 3d |
| I-10 | Source/Human Merge | 冲突合并 | G-12/I-02 | 5d |
| I-11 | Delete Protection | 来源删除不直接发布 | I-10 | 2d |
| I-12 | Draft Rollback | Revision 回退 | D-05 | 2d |
| I-13 | 人工保护 E2E | 覆盖防护 | I-01~12 | 4d |

## Epic J 发布和 Renderer

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| J-01 | Route Manifest Builder | 路由和 301 | D-13 | 3d |
| J-02 | Page/Dataset/Form Projector | 公共投影 | D-13 | 5d |
| J-03 | Public Asset Projector | 复制和 URL | D-08 | 4d |
| J-04 | Publish Validator | BLOCK 规则 | H-15 | 3d |
| J-05 | Atomic Publish Orchestrator | 指针切换 | J-01~04 | 5d |
| J-06 | Rollback Publish | 新 Revision | J-05 | 3d |
| J-07 | Cache Invalidation Event | <60s | C-14/J-05 | 2d |
| J-08 | Renderer Boot/Security | 独立应用 | C-03 | 3d |
| J-09 | Host/Route Resolver | 404/301/410 | J-01/J-08 | 4d |
| J-10 | Pebble/Theme Engine | SSR | A-10/J-08 | 4d |
| J-11 | MVP Block Renderers | 所有 Block | H-10/J-10 | 8d |
| J-12 | Asset Gateway | 缓存和 Header | J-03/J-08 | 3d |
| J-13 | Preview Token | noindex | J-08 | 2d |
| J-14 | Sitemap/robots/SEO | 爬虫输出 | J-09~11 | 4d |
| J-15 | Renderer Cache/Health/Metrics | 运维 | J-08~14 | 3d |
| J-16 | Publish Fault Injection | 旧站不受影响 | J-01~15 | 4d |
| J-17 | SSR Snapshot/Crawl Test | HTML 可读 | J-08~15 | 4d |

## Epic K 转化、分析和推广

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| K-01 | Form Builder/Validator | 表单领域 | D-11 | 3d |
| K-02 | Public Form Submit | 匿名 API | J-08/K-01 | 3d |
| K-03 | Anti-abuse | 限流/蜜罐/去重 | K-02 | 3d |
| K-04 | Lead Normalizer | 线索创建 | D-11/K-02 | 2d |
| K-05 | UTM Attribution | 归因 | K-04 | 2d |
| K-06 | Lead Notification | Adapter | E-02/K-04 | 2d |
| K-07 | Lead Export | CSV/XLSX | K-04 | 2d |
| K-08 | Analytics Event Inbox | 公开事件 | D-12/J-08 | 2d |
| K-09 | Daily Aggregate | 统计任务 | K-08 | 4d |
| K-10 | Promotion Generator | 多渠道内容 | F-05/D-12 | 4d |
| K-11 | UTM Share Link | 可追踪链接 | K-05/10 | 2d |
| K-12 | Save to Cloud | 用户确认回存 | G-06/07/K-10 | 3d |
| K-13 | Conversion E2E | 访问→Lead→报表 | K-01~12 | 5d |

## Epic L 事件、安全、运维和发布

| ID | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---:|
| L-01 | Website Event Dispatcher | Outbox 至少一次 | C-14 | 3d |
| L-02 | Site Event SSE | 版本通知 | L-01 | 3d |
| L-03 | SSE 断线/轮询降级 | 可靠性 | L-02 | 2d |
| L-04 | Feature Flag Off Test | 无 Bean/API/Migration | C-08/09 | 3d |
| L-05 | Module Gate Test | 平台/Workspace/Site | B-10/C-04 | 3d |
| L-06 | Cross Workspace/Site Test | 越权 | E/F/G | 4d |
| L-07 | Prompt Injection Test | Tool 权限 | F-13 | 3d |
| L-08 | XSS/CSP/URL Test | 公开安全 | H-10/J | 4d |
| L-09 | Form Abuse Test | 安全 | K-02/03 | 3d |
| L-10 | Renderer Load Test | 性能 | J | 4d |
| L-11 | Generation Recovery Test | 失败恢复 | H-16 | 4d |
| L-12 | MCP Regression | 云盘助手正常 | G | 3d |
| L-13 | MateClaw Core Regression | Chat/Agent/Cron/Teacher | 全部 | 5d |
| L-14 | Docker/Nginx/Wildcard TLS | 部署 | J | 5d |
| L-15 | DB 权限和备份恢复 | 运维 | J/D | 3d |
| L-16 | Single Workspace Gray | 5 工作日 | 全部 | 5d |
| L-17 | Go/No-Go | 联合发布 | L-01~16 | 1d |


---

# 23. 前端详细任务清单

## Epic M Business Module 前端基础

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| M-01 | BusinessModuleManifest 类型 | 稳定 TS 契约 | 1d |
| M-02 | 静态 Registry | 模块注册 | 1d |
| M-03 | Route Builder | /apps 路由 | 2d |
| M-04 | useBusinessModules | 合并后端状态 | 2d |
| M-05 | Apps 主导航分组 | 动态菜单 | 2d |
| M-06 | /apps 总览 | 应用卡片 | 2d |
| M-07 | /settings/modules 总览 | 治理卡片 | 3d |
| M-08 | Module Status Guard | Disabled/403/ReadOnly | 2d |
| M-09 | Business Capability 模板类型 | 类型安全 | 1d |
| M-10 | Workspace 切换刷新 | 状态重载 | 1d |
| M-11 | Chunk 预热兼容 | Apps Route | 1d |
| M-12 | i18n 模块命名空间 | 独立翻译 | 1d |
| M-13 | Registry/Guard Unit Test | 测试 | 3d |
| M-14 | 启停/权限 E2E | 测试 | 3d |

## Epic N Website Feature 和 Assistant

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| N-01 | Website Manifest/Routes | 独立 Feature | 2d |
| N-02 | Feature-local API | 不改 api/index | 3d |
| N-03 | Site Store | Workspace 隔离 | 2d |
| N-04 | Generation Store | SSE/轮询 | 3d |
| N-05 | Proposal Store | 版本和确认 | 2d |
| N-06 | Preview Store | Token/Revision | 2d |
| N-07 | Site List Page | 站点列表 | 3d |
| N-08 | AI-First New Site Page | 资料+目标 | 4d |
| N-09 | ChatConsole hostRoutePath | 通用最小扩展 | 1d |
| N-10 | Assistant 双栏 Shell | Chat+Preview | 4d |
| N-11 | Generation Progress | 步骤和重试 | 3d |
| N-12 | Missing/Conflict Cards | 补充信息 | 3d |
| N-13 | Proposal Panel | Diff/确认/拒绝 | 4d |
| N-14 | Publish Check Panel | PASS/WARN/BLOCK | 3d |
| N-15 | Chat 默认/Cloud Embed 回归 | 无回退 | 4d |
| N-16 | Assistant E2E | 会话自动建站 | 5d |

## Epic O 来源和内容

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| O-01 | Cloud Source Picker | 文件/文件夹 | 4d |
| O-02 | Knowledge Picker | 知识库 | 3d |
| O-03 | Source List Page | 处理状态 | 3d |
| O-04 | Source Item Table | 失败/跳过/敏感 | 3d |
| O-05 | Manual Read Action | 用户触发 | 2d |
| O-06 | Check Updates Action | 用户触发 Diff | 2d |
| O-07 | Source Diff Drawer | 逐项处理 | 3d |
| O-08 | Content Inventory Page | 结构化实体 | 3d |
| O-09 | Content Origin Badge | 来源显示 | 1d |
| O-10 | Human Lock Toggle | 人工保护 | 2d |
| O-11 | Source/Content E2E | 权限和变化 | 3d |

## Epic P MVP 编辑器

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| P-01 | Editor 三栏 Layout | Page/Preview/Property | 4d |
| P-02 | Page Tree | CRUD/排序 | 3d |
| P-03 | Block Tree | 排序/显示 | 3d |
| P-04 | Text Property Editor | 人工内容 | 2d |
| P-05 | Image Property Editor | 资产替换 | 3d |
| P-06 | Dataset Binding Editor | 集合绑定 | 3d |
| P-07 | Theme Token Editor | 主题 | 3d |
| P-08 | SEO Editor | 页面 SEO | 3d |
| P-09 | Form Editor | 字段和 CTA | 3d |
| P-10 | Dirty Guard | 防丢失 | 2d |
| P-11 | 409 Revision Conflict | 刷新/合并 | 2d |
| P-12 | Human Lock 可视化 | 锁定状态 | 2d |
| P-13 | Editor E2E | 修改/回滚 | 4d |

## Epic Q Preview、发布和域名

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| Q-01 | SSR Preview Frame | Token URL | 3d |
| Q-02 | Device Switcher | 桌面/平板/手机 | 1d |
| Q-03 | Revision Auto Refresh | 预览更新 | 2d |
| Q-04 | Preview Error State | Renderer 诊断 | 2d |
| Q-05 | Publish Dialog | 检查和确认 | 3d |
| Q-06 | Publish Progress | 阶段状态 | 2d |
| Q-07 | Publish History | 历史列表 | 3d |
| Q-08 | Rollback Dialog | 影响预览 | 2d |
| Q-09 | Domain Page | 二级域名 | 3d |
| Q-10 | Redirect Manager | Slug/301 | 2d |
| Q-11 | Publish E2E | 原子和回滚 | 4d |

## Epic R SEO、线索、分析和推广

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| R-01 | SEO Audit Page | 问题列表 | 3d |
| R-02 | Structured Data Preview | JSON-LD | 2d |
| R-03 | Forms Page | 表单列表 | 2d |
| R-04 | Leads Page | 列表和详情 | 3d |
| R-05 | Lead Export | CSV/XLSX | 1d |
| R-06 | Analytics Dashboard | PV/UV/来源 | 4d |
| R-07 | Conversion Funnel | 转化率 | 3d |
| R-08 | Promotion Page | 多渠道内容 | 3d |
| R-09 | Promotion Editor | 人工微调 | 2d |
| R-10 | UTM Link Builder | 分享链接 | 2d |
| R-11 | Save to Cloud Dialog | 路径和文件名确认 | 3d |
| R-12 | Conversion E2E | 表单到报表 | 4d |

## Epic S 配置、治理和前端测试

| ID | 任务 | 产出 | 估算 |
|---|---|---|---:|
| S-01 | Workspace Website Settings | 配置 Revision | 4d |
| S-02 | Site Settings | 继承/固定 | 4d |
| S-03 | Platform Module Page | 状态/健康/限额 | 4d |
| S-04 | Config Diff/Impact | 发布预览 | 3d |
| S-05 | Config History/Rollback | Revision | 3d |
| S-06 | ReadOnly/Disabled UI | 状态守卫 | 2d |
| S-07 | Capability/Nav/Direct Link Test | 权限 | 3d |
| S-08 | Workspace Switch Test | 隔离 | 2d |
| S-09 | Long ID Precision Test | 字符串 ID | 2d |
| S-10 | Responsive Test | 1440/1024/768 | 3d |
| S-11 | Accessibility Basic Test | 键盘/ARIA | 2d |
| S-12 | Frontend Regression | Core/Cloud Embed | 4d |


---

# 24. 推荐 PR 和实施顺序

```text
PR-01  ADR、Spike 和模块骨架
PR-02  Business Runtime 后端
PR-03  Business Module 前端 Registry
PR-04  Website Domain、独立 Flyway 和配置
PR-05  Server Bridge、Conversation、Agent、MCP
PR-06  Generation Pipeline 和 Website Tools
PR-07  Assistant UI、SSE、Proposal、Preview
PR-08  Public Projection、Renderer、SEO、发布
PR-09  MVP Editor 和人工锁定
PR-10  Form、Lead、Analytics、Promotion
PR-11  Security、Performance、Regression
PR-12  Gray Release 和 Go/No-Go
```

所有 PR 默认：

```text
Feature Flag Off
无破坏性迁移
无旧 API 删除
无核心表结构修改
可独立回滚
```

---

# 25. 推荐排期

团队：

```text
后端 2
前端 2
测试 1
产品/UI 0.5
DevOps 0.5
```

如果 Business Runtime 已由 Goal Agent 先落地：

```text
AI 微站 P0：8～10 周
```

如果 Business Runtime 尚未落地：

```text
共享基础：2～3 周
AI 微站 P0：8～10 周
```

## Sprint 0：架构与 Spike

```text
A
B-01~04
M-01~04
```

里程碑：模块边界、独立 Flyway、Agent/MCP/Renderer 路线验证通过。

## Sprint 1：Business Runtime 和模块治理

```text
B 剩余
M 剩余
C-01~08
```

里程碑：平台和 Workspace 可启停 AI 微站，配置可发布和回滚。

## Sprint 2：领域和来源

```text
D
E-01~03
G
O-01~08
```

里程碑：Site、来源和快照可创建，真实用户可以读取云盘资料。

## Sprint 3：AI 自动建站

```text
F
H-01~13
N-01~13
```

里程碑：资料和一句话目标可以生成完整 Draft 网站。

## Sprint 4：Preview 和 Proposal

```text
H-14~18
I
N-14~16
Q-01~04
```

里程碑：会话修改、Diff Proposal、实时 Preview 可用。

## Sprint 5：发布和 Renderer

```text
J
Q-05~11
R-01~02
```

里程碑：SSR 公开站点、SEO、原子发布和回滚可用。

## Sprint 6：人工微调编辑器

```text
P
O-09~11
```

里程碑：人工局部修改和锁定不被 AI 覆盖。

## Sprint 7：转化闭环

```text
K
R-03~12
```

里程碑：表单、线索、分析、推广和云盘回存可用。

## Sprint 8：治理、安全和系统测试

```text
L-01~15
S
```

里程碑：权限、隔离、性能和核心回归通过。

## Sprint 9：灰度和发布

```text
L-16~17
缺陷修复
合并演练
```

---

# 26. 产品验收场景

## AC-01 一句话自动建站

- 用户选择资料并输入一句目标；
- AI 自动生成站点类型、导航、页面、主题、SEO、CTA 和表单；
- 用户不需要手工创建页面或区块；
- 首次 Preview 可直接打开。

## AC-02 缺失和冲突处理

- AI 识别缺失联系方式、来源冲突和敏感信息；
- 先生成可用结果，不因非关键缺失完全阻断；
- 关键事实在发布前必须补充或确认。

## AC-03 会话微调

- 用户自然语言修改页面、主题、SEO 和表单；
- 系统展示 Diff；
- 确认后生成新 Draft Revision；
- 查询结果与页面数据一致。

## AC-04 人工保护

- 人工编辑字段标记 HUMAN_EDITED；
- 锁定字段不被 AI 和来源同步覆盖；
- 冲突产生逐项合并 Proposal。

## AC-05 MCP 来源

- 文件和文件夹读取走现有 McpClientManager；
- 使用当前用户 OBO；
- 跨租户和无权限文件拒绝；
- MCP 不可用时已发布站点正常访问。

## AC-06 发布和回滚

- 发布使用不可变 Revision 和原子切换；
- 发布失败时旧站正常；
- 回滚生成新的发布记录；
- Preview 不进入搜索索引。

## AC-07 匿名和 SEO

- 无需登录；
- 初始 HTML 包含正文；
- Sitemap、Canonical、JSON-LD 和真实状态码正确；
- 不根据 User-Agent 返回不同正文。

## AC-08 表单和转化

- 匿名访客可以提交表单；
- Lead 保存 UTM 和 Published Revision；
- 管理端可查询和导出；
- 分析可查看访问、CTA 和提交转化。

## AC-09 配置治理

- Platform、Workspace、Site 配置作用域正确；
- Revision 发布和回滚可审计；
- Generation Job 保存运行快照；
- Workspace A/B 配置互不影响。

## AC-10 Feature Flag Off

- MateClaw 正常启动；
- 普通 Chat、Agent、MCP、Cron、Teacher 正常；
- Website 路由、Controller、Tool 和 Migration 不加载；
- 已有 `wa_*` 数据保留。

---

# 27. 技术验收标准

## 27.1 正确性

```text
幂等测试 100% 通过
乐观锁冲突返回 409
Proposal 应用事务原子
发布指针原子切换
所有 Site 查询强制 Workspace/Site 校验
非法状态迁移全部拒绝
```

## 27.2 测试覆盖

```text
Domain/Application 关键分支 >= 85%
Adapter Contract Test
H2 和 MySQL CI 必须通过
Kingbase 上线前独立验证
10 条核心 E2E 全部通过
Feature Flag Off 全回归
```

## 27.3 性能

不含外部 LLM：

```text
Site Overview P95 < 500ms
Page List P95 < 700ms
普通 Command P95 < 800ms
Proposal Confirm（20 Page）P95 < 2s
SSE Event P95 < 2s
Publish 20 Page P95 < 10s
缓存命中 TTFB P95 < 200ms
未命中 TTFB P95 < 800ms
Form Submit P95 < 500ms
```

生成：

```text
100 文件以内首版站点 P95 < 5 分钟
单页重生成 P95 < 60 秒
三类站点生成成功率 >= 90%
```

## 27.4 Web 性能

```text
LCP < 2.5s
CLS < 0.1
INP < 200ms
缓存失效 < 60s
```

## 27.5 安全

```text
跨 Workspace/Site 访问全部拒绝
伪造 siteId 不生效
Prompt Injection 不能绕过 Tool 权限
私有云盘 URL 不公开
任意 Script/iframe/外部 Form Action 被拒绝
Renderer 无管理表权限
表单滥用测试通过
```

---

# 28. 上线门禁

必须全部满足：

1. ADR 和 P0 范围签字；
2. Business Runtime 和 Website 独立 Flyway 验证通过；
3. 10 个产品验收场景通过；
4. H2/MySQL/Kingbase 验证完成；
5. Feature Flag Off 回归通过；
6. ChatConsole 和 CloudAgentEmbed 回归通过；
7. MCP OBO 和跨租户测试通过；
8. Renderer 压测和 SEO Crawl 通过；
9. 发布故障注入和备份恢复通过；
10. 无未处理 P0/P1 安全问题；
11. 单 Workspace 灰度不少于 5 个工作日；
12. 产品、研发、测试和运维共同 Go。

---

# 29. 实施前冻结清单

- [ ] 模块定位为第一方 Business App；
- [ ] 正式路由使用 `/apps/website-agent`；
- [ ] AI 自动建站为第一原则；
- [ ] 会话是默认入口；
- [ ] Business Runtime 与 Goal Agent 共用；
- [ ] 前端使用 Apps/Modules Registry；
- [ ] `view/manage/admin:website-agent` 权限冻结；
- [ ] 独立 AutoConfiguration 和 Flyway；
- [ ] Website Domain 不依赖 Server；
- [ ] Agent、Conversation 和 MCP 只通过 Adapter 复用；
- [ ] ChatConsole 只增加通用 `hostRoutePath`；
- [ ] Page DSL 不允许任意 HTML/JS；
- [ ] Public Renderer 独立部署；
- [ ] P0 使用 Wildcard Subdomain；
- [ ] P0 来源更新由用户手工触发；
- [ ] 发布、回滚和删除必须确认；
- [ ] 人工锁定内容不被自动覆盖；
- [ ] 配置 Revision 和运行快照必须实现；
- [ ] Feature Flag Off 是上线硬门禁。

---

# 30. 最终实施结论

```text
资料和一句话目标
→ AI 自动理解
→ AI 自动规划和生成完整网站
→ 用户会话确认和局部微调
→ SSR Preview
→ 原子发布
→ 匿名访问和 SEO
→ 表单、Lead、Analytics、Promotion
→ 用户触发来源更新
→ AI 生成合并建议
```

实施优先级：

```text
Business Module 基础
> AI 自动建站闭环
> 会话与 Proposal
> Preview 和 Publish
> 人工微调
> 转化闭环
> 高级模板和自动同步
```
