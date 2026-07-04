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
| DB 迁移 | 8 | 全新文件 (V100-V103) | 无 |
| **核心文件改动** | **4** | 尾部追加 | **低** (总计 +60 行) |

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
| `model/TeacherExamResultV2.java` | 试卷结果 v2 模型 |
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

## 四、核心框架最小改动

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

## 五、DB 迁移

| 编号 | 内容 |
|------|------|
| V100 | agent_template_binding_columns（新增 6 列） |
| V101 | wiki_business_metadata |
| V102 | wiki_page_route_tags |
| V103 | wiki_page_structure_metadata |

> V101-V103 从 dev-v1 的 V109-V111 重新编号，避免与上游未来迁移冲突。

---

## 六、后续上游同步

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
| `AgentGraphBuilder.java` | promptAugmenters 字段 + 调用 |
| `StateGraphPlanExecuteAgent.java` | interceptors 字段 + 调用 |
