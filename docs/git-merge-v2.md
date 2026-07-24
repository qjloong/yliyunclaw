# dev-v2 合并记录

> 基准：`upstream/dev` (`9dfdae0a`)  
> 策略：增量合并，只添加新文件 + SPI 扩展点，不覆盖上游已有文件  
> 日期：2026-07-04

## 合并概览

| 类别 | 文件数 | 方式 | 上游冲突风险 |
|------|--------|------|------------|
| Teacher 业务模块 | 16 | 全新文件 | 无 |
| Wiki 扩展 | 17 | 全新文件 | 无 |
| SPI 架构 | 4 | 全新文件 | 无 |
| DB 迁移 | 12 | 全新文件 (V9001-V9006, H2+MySQL) | 无（V9xxx 系列永不冲突） |
| UI 新增文件 | 2 | 全新文件 (TeacherOps.vue, ProjectChangesPanel.vue) | 无 |
| UI API/Types 增强 | 2 | 追加代码块到上游文件末尾 | 低 |
| 导出工具 | 2 | 全新文件 | 无 |
| **核心框架改动** | **4** | 尾部追加 | **低** (总计 +60 行) |
| **Controller API 修复** | **2** | 方法名适配 | **低** |

---

## 一、Teacher 业务模块 (`vip.mate.teacher.*`)

全部独立文件，无上游修改：

| 文件 | 说明 |
|------|------|
| `service/TeacherIntentService.java` | 教师意图识别、身份问答、模块检测 |
| `service/TeacherSkillDefinitionService.java` | 教师 Skill 定义（命题闭环、质量审核） |
| `service/TeacherRulePackService.java` | 6 模块规则包管理 |
| `service/TeacherAcceptanceService.java` | 默认验收清单 |
| `controller/TeacherRulePackController.java` | 规则包管理接口 |
| `controller/TeacherSkillController.java` | Skill 绑定管理接口 |
| `model/TeacherTurnContext.java` | 教师轮次上下文 |
| `model/TeacherRulePack.java` | 规则包实体 |
| `model/TeacherRulePackView.java` | 规则包视图（含覆盖状态） |
| `model/TeacherExamResultV2.java` | 试卷结果 v2 模型 |
| `model/TeacherSkillDefinition.java` | Skill 定义实体 |
| `model/TeacherSkillBindingView.java` | Skill 绑定视图 |
| `model/QuestionTypeRule.java` | 题型规则模型 |
| `resources/teacher-rules/*.json` | 6 个模块的规则 JSON |
| `resources/templates/teacher-exam-assistant.json` | 内置模板定义 |

**排除的文件**（依赖不存在的 `vip.mate.harness` 包）：
- `TeacherImprovementController.java`
- `TeacherImprovementDraftService.java`
- `TeacherImprovementDraft.java`

---

## 二、Wiki 扩展模块

### 分类器 (`vip.mate.wiki.classifier.*`)
- `MaterialTypeClassifyResult`, `WikiMaterialTypeClassifier`, `WikiStructureExtractor`

### DTO 扩展 (`vip.mate.wiki.dto.*`)
- `RawSearchRef`, `WikiDerivedView`
- `WikiDomainProfileMaterialType`, `WikiDomainProfileMetadataField`, `WikiDomainProfileOption`
- `WikiIntentFilter`, `WikiMaterialCoverageReport`

### Service 扩展 (`vip.mate.wiki.service.*`)
- `WikiBusinessModuleDeriver`, `WikiDomainProfileLoader`, `WikiDomainProfileRegistryService`
- `WikiIntentMaterialMapper`, `WikiMaterialCoverageService`
- `WikiMaterialProcessingRecipe`, `WikiMaterialProcessingRouter`

---

## 三、SPI 扩展点架构

### Plugin-API 模块（`mateclaw-plugin-api`）
- `AgentContext` — 运行时上下文 record（agentId, templateId, profileId 等）
- `AgentPromptAugmenter` — 提示词增强接口（build-time）

### Server 模块（`mateclaw-server`）
- `vip.mate.agent.interceptor.AgentExecutionInterceptor` — 执行拦截接口（per-turn）
  - `supports()`, `beforeExecution()`, `transformMessage()`
- `TeacherAgentExtension` — 教师扩展类，实现两个 SPI
  - 身份/用法问答 → 直接回答
  - 出题意图 → 命题方案 → 等待确认
  - 确认出题 → 注入规则包 + Skill → 进入 plan-execute

---

## 四、UI 前端合并

### 新增独立文件
| 文件 | 说明 |
|------|------|
| `views/TeacherOps.vue` | 教师管理页面（规则包查看、Skill 绑定、改进审核） |
| `components/chat/ProjectChangesPanel.vue` | 项目变更与生成文件面板 |

### 追加上游文件（不覆盖原有内容）
| 文件 | 追加内容 |
|------|---------|
| `api/index.ts` | `teacherRulePackApi` + `teacherSkillApi`（接在 `templateApi` 之后） |
| `types/index.ts` | `QuestionTypeRule`, `TeacherRulePack`, `TeacherRulePackView`, `TeacherSkillDefinition`, `TeacherSkillBindingView`, `TeacherImprovementDraft`, `TemplateKnowledgeBindingHealth` |

### 未合并的 UI 修改文件（及原因）
以下 11 个文件在 dev-v1 中为 M（Modified），但 **未合并** 到 dev-v2：
- `ChatConsole.vue`, `MessageBubble.vue`, `MessageList.vue`
- `useMarkdownRenderer.ts`, `stores/useWikiStore.ts`
- `Wiki/index.vue`, `RawMaterialPanel.vue`, `RelatedPagesPanel.vue`
- `WikiConfig.vue`, `WikiGraphToolbar.vue`, `Memory/index.vue`

**原因**：dev-v1 基于旧版本上游代码，这些文件的大量差异实际上是 **上游新增功能的缺失**（dev-v1 少 ~26K 行 UI 代码），而非教师特有改动。盲目覆盖会导致上游新功能丢失。

> 教师功能的 UI 交互完全由 `TeacherOps.vue` + `ProjectChangesPanel.vue` + `TeacherAgentExtension`（服务端）提供，不需要上述 11 个文件的改动。

---

## 五、导出工具

| 文件 | 说明 |
|------|------|
| `GeneratedDiskFileRegistry.java` | 生成文件磁盘注册表 |
| `GeneratedFileDiskTokenService.java` | 生成文件磁盘令牌服务 |

---

## 六、核心框架最小改动

以下 4 个文件是合并过程中唯一修改的上游文件，**每个改动 < 20 行**：

### BaseAgent.java（+18 行，尾部追加）
```java
// 新增字段（第 112 行后）
protected String templateId;
protected String profileId;
protected String capabilityPackId;
protected String pluginKey;
protected String templateMetadataJson;
protected String knowledgeBaseIdsJson;
protected String runtimeMode;

// 新增方法
public AgentContext toAgentContext() { ... }
```

### AgentEntity.java（+7 行，尾部追加）
```java
// 新增模板绑定列
@TableField(value = "template_id") private String templateId;
@TableField(value = "profile_id") private String profileId;
@TableField(value = "capability_pack_id") private String capabilityPackId;
@TableField(value = "plugin_key") private String pluginKey;
@TableField(value = "template_metadata_json") private String templateMetadataJson;
@TableField(value = "knowledge_base_ids_json") private String knowledgeBaseIdsJson;
```

### AgentGraphBuilder.java（+22 行）
- 第 155 行：添加 `List<AgentPromptAugmenter> promptAugmenters` 字段（@Autowired）
- 第 494 行后：设置 7 个 template 字段 + runtimeMode
- 第 1691 行：调用 prompt augmenters 增强 system prompt

### StateGraphPlanExecuteAgent.java（+13 行）
- 第 53 行：添加 `List<AgentExecutionInterceptor> interceptors` 字段（@Autowired）
- 第 83 行：在 `chatStructuredStream` 中调用拦截器

---

## 七、API 兼容性修复

Teacher Controller 中使用了 dev-v1 的 `SystemSettingService.getRawValue()` 方法，上游已重命名为 `getString()`：

| 文件 | 修复 |
|------|------|
| `TeacherSkillController.java` | `getRawValue(...)` → `getString(...)` |
| `TeacherRulePackController.java` | `getRawValue(...)` → `getString(...)`（2 处） |

---

## 八、DB 迁移

> **编号规则**：采用 `V9001` 起独立编号，与上游 `V1-V999` 永不冲突，后续拉取更新无需处理迁移编号。

| 编号 | 内容 |
|------|------|
| V9001 | agent_template_binding_columns（新增 6 列：template_id, profile_id, capability_pack_id, plugin_key, template_metadata_json, knowledge_base_ids_json） |
| V9002 | wiki_business_metadata |
| V9003 | wiki_page_route_tags |
| V9004 | wiki_page_structure_metadata |
| V9005 | register_html_render_tools |
| V9006 | register_understand_anything_tool |

---

## 九、提交历史

```
b3520d76 feat: merge teacher UI files, export tools, and API types
82f45144 feat: incremental merge teacher-agent v2 via SPI extension points
```
（共 2 个提交，基于 `9dfdae0a` upstream/dev）

---

## 十、后续上游同步

```bash
git fetch upstream --no-tags
git merge upstream/dev
```

冲突只会出现在 4 个核心文件中，每个文件改动量 < 30 行，手动确认保留扩展点即可。

### 冲突解决清单（上游 merge 时按此操作）

| 文件 | 保留内容 |
|------|---------|
| `BaseAgent.java` | template 字段 + toAgentContext() |
| `AgentEntity.java` | template 列定义 |
| `AgentGraphBuilder.java` | promptAugmenters 字段 + 调用 + template 字段设置 |
| `StateGraphPlanExecuteAgent.java` | interceptors 字段 + 调用 |
| `api/index.ts` | teacherRulePackApi + teacherSkillApi |
| `types/index.ts` | 教师相关 interface 定义 |
| `TeacherRulePackController.java` | getRawValue → getString 修复 |
| `TeacherSkillController.java` | getRawValue → getString 修复 |

---

## 十一、2026-07-24 上游合并实践记录

> 上游 `upstream/dev` 最新 `e294b325`（07-24），63 次提交，变更 300+ 文件。
> 合并到 `dev-v2`（`72aa21ef`），策略：冲突取上游（logo/主题除外）。

### 11.1 合并过程

```
git fetch upstream dev
git stash                  # 暂存未提交改动
git merge FETCH_HEAD       # 仅 3 文件冲突：.gitignore、AgentGraphBuilder.java、package.json
git checkout --theirs ...  # 全部取上游
git commit                 # merge commit: 72aa21ef
```

### 11.2 问题汇总与修复

#### P1：pnpm-workspace.yaml 缺 packages 字段
- **现象**：`pnpm install` → `packages field missing or empty`
- **原因**：上游 `pnpm-workspace.yaml` 只有 `allowBuilds`，缺 `packages`
- **修复**：添加 `packages: ['.']`

#### P2：MetaY 定制块全部丢失
- **现象**：`git merge` 自动合并后，13 个文件中的 MetaY 标注全部消失
- **原因**：上游对同文件有大量改动（如 ChatConsole.vue +176/-36），git auto-merge 选了上游版本
- **涉及文件**：后端 5（AgentEntity、TemplateDTO、TemplateService、ToolPolicyResolver、ToolGuardEngine）+ 前端 8（ChatConsole、Agents、MessageList、ContentSegment、ChatInput、types、api、Workspaces/index）
- **修复**：手动重写全部 MetaY 块到合并后代码中（+397/-9 行）
- **教训**：后续合并后用 `git grep "MetaY custom"` 验证块是否存在

#### P3：ChatConsole.vue 多余 `</div>`
- **现象**：Vite 编译报错 `Invalid end tag at line 285`
- **原因**：恢复 MetaY 块时，workspace-switcher 模板段多带了一个 `</div>`
- **修复**：删除多余标签

#### P4：Flyway 版本冲突 V167–V171
- **现象**：Spring Boot 启动报错 `Detected resolved migration not applied to database: 167-171`
- **原因**：上游新增 V167–V171（VolcanoEngine、content-studio 等），本地 H2 的 `flyway_schema_history` 无这些版本的执行记录
- **修复**：清空 H2 数据库文件重建

#### P5：首次合并遗漏的 DB 列（`plugin_key`、`knowledge_base_ids_json`）
- **现象**：`GET /api/v1/agents` 500 → `Column "xxx" not found`
- **原因**：首次合并（`82f45144`）时 `AgentEntity` 新增了字段但遗漏了对应的 DB 迁移。旧数据库侥幸有这些列，清空重建后暴露
- **补救迁移**：
  | 迁移 | 列名 | 类型 |
  |------|------|------|
  | V9007 | `home_subtitle`, `home_quick_starts_json` | VARCHAR(512) / CLOB |
  | V9010 | `plugin_key` | VARCHAR(64) |
  | V9011 | `knowledge_base_ids_json` | CLOB |
- **操作**：Rebuild → 重启 → Flyway 自动按序号执行未应用的迁移

#### P6：内置定制 Agent 的 pluginKey/profileId/capabilityPackId 绑定未生效
- **现象**：教师出题助手 / 编码工程助手作为内置模板应用后，`TeacherAgentExtension.supports()` 返回 false，扩展规则（出题格式、规则约束）不生效；新建会话时仍按通用 agent 行为运行。
- **根因**：`TemplateDTO` 缺少 `pluginKey`/`profileId`/`capabilityPackId`/`templateMetadataJson`/`templateVersion`/`templateCategory`/`templateDomain` 字段；`TemplateService.applyTemplate` 仅写入 `name/description/systemPrompt` 等基础字段，不写入模板绑定的元数据。`TeacherIntentService.isTeacherAgent(...)` 通过 `pluginKey` 优先识别，缺失时按 `templateId`/`profileId` 兜底，agent 全部为 null → 识别失败。
- **修复**：
  1. `TemplateDTO.java` 新增上述 7 个字段
  2. `TemplateService.applyTemplate` 在系统提示词之后、`setWorkspaceId` 之前调用对应的 setter
  3. 两个模板 JSON（`teacher-exam-assistant.json`、`coding-agent.json`）补充同名字段
  4. 6 份数据 seed（`data-zh.sql`/`data-en.sql`/`data-mysql-zh.sql`/`data-mysql-en.sql`/`data-kingbase-zh.sql`/`data-kingbase-en.sql`）追加 1000000010/1000000011 两条内置 agent 行
- **新内置 agent 字段**：
  - 1000000010 名师出题助手：`pluginKey=builtin.teacher_exam`，`profileId=teacher_exam_assistant_profile`，`capabilityPackId=capability.education.junior_chinese_exam`，`templateId=builtin.teacher_exam_assistant`
  - 1000000011 编码工程助手：`pluginKey=builtin.coding_agent`，`profileId=coding_agent_profile`，`capabilityPackId=capability.developer.codex_workspace`，`templateId=builtin.coding_agent`

#### P7：内置定制 Agent 提示词过于繁琐
- **现象**：模板的 `systemPrompt` 有 10+ 段（300+ 字），与主分支默认 agent（1 句话）不一致，对话前缀容易触达模型上下文上限。
- **根因**：首版按"完整规则"写入系统提示词；实际上规则细节更适合放在 workspace 文件（AGENTS.md / QUESTION_FORMAT.md / PROJECT_CACHE.md 等）。
- **修复**：每个 agent 的 `systemPrompt` 缩为 1 段（教师 1 段、编码 1 段），规则全部下沉到 workspace 文件：
  - 教师：`AGENTS.md` 命题规则 / `QUESTION_FORMAT.md` 试题模板 / `MEMORY.md` 记忆 / `ACCEPTANCE.md` 验收
  - 编码：`AGENTS.md` 编码规则 / `PROFILE.md` 画像 / `PROJECT_CACHE.md` 项目理解 / `MEMORY.md` / `ACCEPTANCE.md`
- 同步更新 6 份 seed 中的 `systemPrompt` 字段

#### P8：ChatInput 组件解耦（ChatInputWorkspaceBar 副本模式）
- **目标**：把工作区切换 + 权限入口从 ChatConsole 顶部移到 ChatInput 底部（还原 v1 布局），同时保持 `ChatInput.vue` 零修改以避免合并冲突。
- **策略**：复制上游 `ChatInput.vue` → `ChatInputWorkspaceBar.vue`，在新组件中追加 workspace bar。
- **文件变更**：
  | 文件 | 操作 |
  |------|------|
  | `ChatInput.vue` | **还原为上游版本**（删 risk 徽标 MetaY）—— 下次合并零冲突 |
  | `ChatInputWorkspaceBar.vue` | **新建**（复制 ChatInput + workspace bar） |
  | `ChatConsole.vue` | 替换 `ChatInput` → `ChatInputWorkspaceBar`，删旧 `chat-ws-switch` |
- **Workspace bar 特性**（v1 风格对齐）：
  - **+ 按钮**（`.composer-icon-btn`）：占位 + 菜单入口
  - **完全访问 pill**（`.composer-pill-btn.is-full-access`，红边+淡红底）：点击触发 `open-permission` 事件
  - **🗂️ 工作区 chip**（`.composer-workspace-chip`）：显示当前工作区名称 + 路径
  - **模型 chip**（`.composer-model-chip`）：显示 `activeModelLabel`，如 "DeepSeek (DeepSeek V4 Pro)"
  - 全部 v1 风格的圆角（12px）、高度（24px）、阴影、hover 态
- **合并策略**：下次 `git merge upstream/dev` 后，仅需确认 `ChatConsole.vue` 中的 `<ChatInputWorkspaceBar>` 引用未被改回 `<ChatInput>`；`ChatInputWorkspaceBar.vue` 是全新文件，永远不被上游触碰。

#### P9：上一步样式与 v1 实际不符（修正 P8 实施）
- **原因**：P8 中自定义的 `.workspace-bar` 风格与 v1 的 `.composer-footer-left` + `.composer-pill-btn` + `.composer-workspace-chip` 风格不一致，v1 使用圆角 pill + 红边 danger 强调"完全访问"权限等级。
- **修复**：完全替换为 v1 风格类名（`.composer-footer-left`、`.composer-icon-btn`、`.composer-pill-btn.is-full-access`、`.composer-workspace-chip`、`.composer-model-chip`），颜色变量沿用 v1 的 `var(--mc-primary)` / `var(--mc-danger)` / `color-mix()`。

#### P10：底部栏按钮需要 popover 弹层（v1 完整交互）
- **现象**：完全访问按钮、工作区 chip 在 v1 中点击会弹出 popover（切换权限级别 / 切换工作区 / 权限配置），但当前只触发跳转，没有弹层交互。
- **修复**（仍在 `ChatInputWorkspaceBar.vue` 内部，不外溢）：
  - 状态：`permissionMenuOpen` / `workspaceMenuOpen` / `permissionLevel: 'full' | 'limited'`
  - 完全访问 popover：标题「选择访问级别」+ 两个 menu-item（完全访问 / 受限访问），右侧 ✓ 标记当前级别
  - 工作区 popover：标题「当前工作区」+ 当前工作区摘要（名称+路径）+ 两个 menu-item（切换工作区 / 工作区权限）
  - 外部点击关闭：`document.addEventListener('click', onDocumentClick)`，`composer-menu-wrap` 内部 @click.stop 阻止冒泡
  - 下拉箭头：`▾` caret 跟在文字后
  - 弹层定位：`position: absolute; bottom: calc(100% + 6px)`（向上弹出，不挡输入框）
- 触发动作 emit 出去由父组件（`ChatConsole.vue`）接：open-permission / switch-workspace / switch-permission-level
- 组件内化弹层的好处：父组件 0 改动即可使用，后续 ChatConsole.vue 调整工作区设置页时不影响弹层。

#### P8：ChatInput 组件解耦（ChatInputWorkspaceBar 副本模式）
- **目标**：把工作区切换 + 权限入口从 ChatConsole 顶部移到 ChatInput 底部（还原 v1 布局），同时保持 `ChatInput.vue` 零修改以避免合并冲突。
- **策略**：复制上游 `ChatInput.vue` → `ChatInputWorkspaceBar.vue`，在新组件中追加 workspace bar。
- **文件变更**：
  | 文件 | 操作 |
  |------|------|
  | `ChatInput.vue` | **还原为上游版本**（删 risk 徽标 MetaY）—— 下次合并零冲突 |
  | `ChatInputWorkspaceBar.vue` | **新建**（复制 ChatInput + workspace bar） |
  | `ChatConsole.vue` | 替换 `ChatInput` → `ChatInputWorkspaceBar`，删旧 `chat-ws-switch` |
- **Workspace bar 特性**：
  - 显示当前工作区名称 + 本地目录路径（位于输入框底部）
  - 切换工作区按钮 → emit `switch-workspace`
  - 权限配置按钮（锁图标）→ emit `open-permission`
  - `WorkspaceSwitcher` 全局组件复用，无需副本
- **合并策略**：下次 `git merge upstream/dev` 后，仅需确认 `ChatConsole.vue` 中的 `<ChatInputWorkspaceBar>` 引用未被改回 `<ChatInput>`；`ChatInputWorkspaceBar.vue` 是全新文件，永远不被上游触碰。

### 11.3 合并后验证清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 后端编译 | ✅ | 5 文件 lint 0 错误 |
| 前端编译 | ✅ | ChatConsole 多余 `</div>` 已修 |
| pnpm install | ✅ | workspace.yaml 已补 packages |
| Flyway 迁移 | ✅ | V167–V171 + V9001–V9009 + V9010 |
| plugin_key 列 | ✅ | V9010 补充 |
| 新文件完整性 | ✅ | ChatHome、TeacherExamResultBlock、PolicyPanel、WorkspacePolicy* 等完好 |

### 11.4 下次合并备忘

```bash
# 1) 拉取合并
git fetch upstream dev
git merge upstream/dev

# 2) 验证 MetaY 块未被冲掉
git grep "MetaY custom" | wc -l    # 应有 20+ 处

# 3) 如有丢失，从 pre-merge commit 提取块：
git show <pre-merge-commit>:path/to/file | # 抽取 MetaY 块，手动挂到合并后文件

# 4) 清空本地 DB（如有 schema 变更）
rm -f data/mateclaw.mv.db data/mateclaw.trace.db

# 5) 常见问题速查
# - pnpm: 检查 pnpm-workspace.yaml 有 packages 字段
# - Flyway: 检查 flyway_schema_history 与 migration 文件版本一致
# - 缺失列: 对应 Entity 的 @TableField 必须在某处有 migration
```

### 11.5 架构反思：Agent-Plugin 绑定的演进方案

#### 当前方案：直接在 AgentEntity 扩展字段

```
AgentEntity 新增字段：templateId / profileId / capabilityPackId / pluginKey
TeacherIntentService.isTeacherAgent() → 读 agent 实体字段
```

| 优 | 劣 |
|----|----|
| 零额外查询，MyBatis-Plus 自动映射 | 每次上游改 AgentEntity schema 都有冲突风险 |
| 实现简单，Plugin Extension 直接读 agent 属性 | 遗漏 DB migration 会导致启动失败（本次的核心教训） |
| 一次 `SELECT * FROM mate_agent` 拿到所有信息 | TemplateService 需要知道所有 AgentEntity setter |

> **评估**：当前 4 个字段稳定，上游不太可能为相同语义新增列。本次合并的实际痛点是 **MetaY 块丢失 ** + **DB 迁移遗漏**，而非方案本身。

#### 演进方案 A：独立绑定表（推荐，如需重构）

```sql
CREATE TABLE mate_agent_binding (
  agent_id         BIGINT PRIMARY KEY REFERENCES mate_agent(id),
  template_id      VARCHAR(128),
  plugin_key       VARCHAR(64),
  profile_id       VARCHAR(128),
  capability_pack_id VARCHAR(256),
  metadata_json    CLOB,
  create_time      TIMESTAMP DEFAULT NOW(),
  update_time      TIMESTAMP DEFAULT NOW()
);
```

TeacherAgentExtension 改为查询此表，AgentEntity 回退保留 `templateId` 即可。

| 优 | 劣 |
|----|----|
| **AgentEntity 零修改** → 合并零冲突 | 多一次查询 / JOIN |
| 插件绑定与 agent 生命周期完全解耦 | 需要新 service / migration / 清理逻辑 |
| 可独立演进扩展字段 | Agent 删除时需级联（CASCADE 或手动） |

#### 演进方案 B：template_metadata_json 承载全部绑定信息

AgentEntity 仅保留 `template_metadata_json`（已有），所有绑定信息都编码为 JSON：
```json
{"pluginKey":"builtin.teacher_exam","profileId":"...","capabilityPackId":"..."}
```

| 优 | 劣 |
|----|----|
| AgentEntity **零新列** | JSON 无法被 DB 索引，不能按 plugin 查 agent |
| 扩展性最强，加新 key 无需迁移 | 每次判断都要 JSON 反序列化 |
| TemplateService 只需 `setTemplateMetadataJson` | 字段缺失/格式错误无编译期保护 |

#### 建议

| 时机 | 做法 |
|------|------|
| **当下** | 维持现状（AgentEntity 4 字段）。合并后已稳定，冲突面已知可控 |
| **下次合并若 AgentEntity 再冲突** | 切换到方案 A（独立绑定表），一次性消除所有 AgentEntity 冲突面 |
| **需求驱动的扩展** | 如新增 agent 类型需要更多绑定字段，直接用方案 A，不再给 AgentEntity 加列 |

---

## 十二、2026-07-24 最终变更清单 & 合并操作指南

> 状态：上游 `e294b325` 已合并，本分支含全部定制功能，待提交。
> 改动文件：**27 修改 + 24 新增 = 51 文件**。

### 12.1 修改文件（27，需 MetaY 标注）

#### 后端（7 修改）

| 文件 | 改什么 | 合并风险 |
|------|--------|---------|
| `AgentEntity.java` | +5 字段（templateId/profileId/capabilityPackId/pluginKey/templateMetadataJson）+ home 2 字段 | 🟡 低（字段追加，上游极少改 entity） |
| `TemplateDTO.java` | +HomeQuickStart 内部类 + profile/capability/plugin/metadata 字段 | 🟡 低 |
| `TemplateService.java` | applyTemplate 拷贝首页配置 + 模板绑定字段 | 🟡 低（方法尾部追加） |
| `ToolPolicyResolver.java` | +applyWorkspacePolicy() 方法 + import | 🟢 极低（新增方法，无上游改动） |
| `ToolGuardEngine.java` | 注入 WorkspacePolicyService + evaluate() 策略叠加 | 🟡 低（构造函数 + 方法内追加） |
| `application.yml` | H2 URL 加 FILE_LOCK=SOCKET;WRITE_DELAY=20;LOCK_TIMEOUT=10000 | 🟢 极低（仅本地 H2） |

#### 前端（14 修改）

| 文件 | 改什么 | 合并风险 |
|------|--------|---------|
| `main.css` | 全套 CSS 变量改 v1 浅蓝（sidebar/chat/bubble/glow/shadow） | 🔴 高（上游也维护此文件） |
| `ChatConsole.vue` | 替换 ChatInput→ChatInputWorkspaceBar + ws 计算属性 + openPluginsPage + 删除旧 ws-switch + 样式 | 🔴 高（上游也修改此文件） |
| `Agents.vue` | +首页 Tab + 草稿编辑 + 样式 | 🟡 中 |
| `MessageList.vue` | +ChatHome 模板 + Props | 🟡 中 |
| `ContentSegment.vue` | +teacherExamPayload + TeacherExamResultBlock 模板 | 🟢 低 |
| `types/index.ts` | +AgentHomeQuickStart + Agent 2 字段 | 🟢 低（尾部追加） |
| `api/index.ts` | +workspacePolicyApi 对象 | 🟢 低（尾部追加） |
| `Workspaces/index.vue` | +策略按钮 + PolicyPanel/ProjectPermissionPanel 对话框 | 🟢 低 |
| `pnpm-workspace.yaml` | +packages: ['.'] | 🟢 极低（仅本地修复） |

#### 配置/依赖（6 修改）

| 文件 | 改什么 | 处理 |
|------|--------|------|
| `.gitignore` | 合并上游 | 每次取上游 |
| `package.json` | 合并上游 | 每次取上游 |
| `data-zh.sql` 等 6 份 seed | +1000000010/1000000011 内置 agent | 🟡 中（上游也新增 seed） |

### 12.2 新增文件（24，零冲突）

#### 后端新增（10）

| 文件 | 说明 |
|------|------|
| `WorkspacePolicyEntity.java` | 工作区策略实体 |
| `WorkspacePolicyService.java` | 策略 CRUD |
| `WorkspacePolicyMapper.java` | MyBatis-Plus Mapper |
| `WorkspacePolicyController.java` | REST API |
| `WorkspaceProjectPermissionEntity.java` | 项目权限实体 |
| `WorkspaceProjectPermissionService.java` | 权限 CRUD |
| `WorkspaceProjectPermissionMapper.java` | Mapper |

#### 前端新增（4）

| 文件 | 说明 |
|------|------|
| `ChatHome.vue` | 聊天首页（副标题+快捷入口） |
| `TeacherExamResultBlock.vue` | Teacher 试卷结果渲染 |
| `ChatInputWorkspaceBar.vue` | ChatInput 副本 + v1 composer 底栏 |
| `PolicyPanel.vue` | 策略可视化编辑器 |
| `ProjectPermissionPanel.vue` | 项目权限管理 |

#### DB 迁移新增（10）

| 版本 | 内容 |
|------|------|
| V9007 | `mate_agent` +home_subtitle +home_quick_starts_json |
| V9008 | 新建 `mate_workspace_policy` |
| V9009 | 新建 `mate_workspace_project_permission` |
| V9010 | `mate_agent` +plugin_key |
| V9011 | `mate_agent` +knowledge_base_ids_json |

### 12.3 下次合并操作流程

```bash
# ═══ 第 1 步：拉取合并 ═══
git add -A && git stash                  # 暂存本地未提交改动
git fetch upstream dev                   # 拉上游
git checkout dev-v2                      # 切到本地分支
git merge upstream/dev                   # 合并

# ═══ 第 2 步：冲突处置（保持此规则） ═══
# - .gitignore / package.json / pom.xml / AgentGraphBuilder.java → git checkout --theirs
# - main.css → 手工合并 v1 CSS 变量（第 2-43 行）
# - ChatConsole.vue / Agents.vue → 保留上游，重新挂 MetaY 块
# - seed SQL → 保留上游新行 + 追加我们的 1000000010/1000000011

# ═══ 第 3 步：验证 MetaY 块 ═══
grep -rn "MetaY custom" mateclaw-server/src mateclaw-ui/src | wc -l
# 预期 25+ 处

# ═══ 第 4 步：重建 & 清库 ═══
mvn clean compile -pl mateclaw-server -am -DskipTests
rm -f data/mateclaw.mv.db data/mateclaw.trace.db
cd mateclaw-ui && pnpm install && npm run dev
```

### 12.4 冲突分级 & 处置矩阵

| 级 | 文件 | 处置 |
|----|------|------|
| 🔴 | `main.css` | **手工合并**：上游新增变量保留，v1 色值覆盖。每次对 10 行 |
| 🔴 | `ChatConsole.vue` | **保留上游模板** → 重新挂 4 段 MetaY（import / computed / 事件 / 样式） |
| 🟡 | `Agents.vue` | 保留上游 template → 重新挂 1 段 MetaY（首页 Tab + 面板） |
| 🟡 | `MessageList.vue` | 保留上游 → 重新挂 ChatHome + Props |
| 🟡 | `AgentEntity.java` | git 自动合并（其他都改不同字段） |
| 🟡 | 6 份 seed SQL | 保留上游新行 → 追加 1000000010/1000000011 |
| 🟢 | 其余 21 修改文件 | 尾部追加，git 大概率自动合并 |
| 🟢 | 24 新增文件 | 永不冲突 |

### 12.5 快速恢复清单

| 步骤 | 动作 | 耗时 |
|------|------|------|
| ① | `git merge upstream/dev` | 5 min |
| ② | 解决 🔴 文件冲突（main.css + ChatConsole） | 10 min |
| ③ | `grep "MetaY custom"` 确认 25+ 处 | 1 min |
| ④ | 补遗（如有 MetaY 块被冲） — 从旧版抽取重挂 | 15 min |
| ⑤ | 清 H2 DB + Rebuild | 3 min |
| ⑥ | 前端 pnpm install + dev | 2 min |
| ⑦ | 冒烟测试（A 首页 → B Teacher → C 切换 → D 策略） | 5 min |
| **总计** | | **~40 min** |
