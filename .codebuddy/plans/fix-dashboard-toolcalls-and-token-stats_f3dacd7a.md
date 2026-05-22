---
name: fix-dashboard-toolcalls-and-token-stats
overview: 修复仪表盘工具调用统计始终为0的问题，以及Token按模型统计不准确（出现空provider/unknown model记录）的问题。
todos:
  - id: fix-dashboard-tool-calls
    content: 修改 DashboardService 从 assistant 消息 metadata 中解析 toolCalls 数组长度
    status: completed
  - id: fix-usage-final-react
    content: 修改 StateGraphReActAgent 两处 _usage_final 事件，移除 token>0 发送限制
    status: completed
  - id: fix-usage-final-plan
    content: 修改 StateGraphPlanExecuteAgent _usage_final 事件，移除 token>0 发送限制
    status: completed
  - id: fix-token-usage-query
    content: 修改 TokenUsageService 查询条件排除 token=0 消息并添加空值兜底
    status: completed
    dependencies:
      - fix-usage-final-react
      - fix-usage-final-plan
---

## 产品概述

修复 MateClaw 后台统计系统的两个数据异常问题。

## 核心功能

1. **仪表盘工具调用统计修复**：用户首页仪表盘中的"工具调用"统计项当前始终为 0，需改为正确统计实际发生的工具调用次数。
2. **Token 统计按模型归类修复**：管理员在"设置-Token 统计"页面中按模型统计时，出现 provider 为空（显示为 `-`）、model 为 `unknown` 且 token 为 0 的异常记录，导致 deepseek、kimi、ollama 等供应商的统计数据展示混乱。

## 技术栈

- 后端：Spring Boot + Java 17 + MyBatis-Plused
- 数据库：H2（开发）/ MySQL（生产）

## 实现方案

### 问题1：仪表盘工具调用统计为0

**根因**：`DashboardService` 通过查询 `role='tool'` 的消息数量来统计工具调用次数，但整个代码库中没有任何路径将工具调用结果保存为 `role='tool'` 的独立消息。工具调用信息仅保存在 `assistant` 消息的 `metadata` JSON（包含 `toolCalls` 数组）中。

**修复策略**：修改 `DashboardService.queryStats()`，不再查询 `role='tool'` 的消息。改为查询 `role='assistant'` 且 `metadata` 包含 `"toolCalls"` 的消息，使用 `ObjectMapper` 解析 `metadata` JSON，累加 `toolCalls` 数组长度作为工具调用总次数。通过 `LIKE` 预过滤减少需要解析的消息量，保证性能。

### 问题2：Token 统计按模型不准确

包含三个关联修复：

**修复 2a：确保 `_usage_final` 事件总是携带 provider/model 信息**

- **根因**：`StateGraphReActAgent` 和 `StateGraphPlanExecuteAgent` 在流结束时发送 `_usage_final` 事件，但条件是 `finalPromptTokens > 0 || finalCompletionTokens > 0`。对于不返回 usage 的模型（如 ollama 某些配置），该事件被跳过，导致 `StreamAccumulator` 无法记录 `runtimeModelName` 和 `runtimeProviderId`，保存的 assistant 消息缺少供应商/模型信息。
- **策略**：移除 token > 0 的条件限制，让 `_usage_final` 事件始终发送，确保所有 assistant 消息都能正确关联到 provider 和 model。

**修复 2b：排除无实际 token 消耗的干扰消息**

- **根因**：`TokenUsageService` 的查询条件包含 `.isNotNull(MessageEntity::getTokenUsage)`。但 `ConversationService.saveMessage()` 中 `tokenUsage = promptTokens + completionTokens`，即使都为 0 也会写入 0 而非 null。导致停止标记（`[已停止生成]`）、错误标记（`[错误] ...`）、中断标记等特殊消息也被纳入统计。这些消息没有实际的 LLM 调用，因此 provider/model 为空，在前端显示为 `-` / `unknown`。
- **策略**：修改查询条件，移除 `isNotNull(tokenUsage)`，仅保留 `promptTokens > 0 || completionTokens > 0`。只统计有实际 token 消耗的 assistant 消息，彻底排除干扰记录。

**修复 2c：合并空 provider 的兜底展示（代码层防御）**

- 在 `TokenUsageService.buildSummary()` 中，对 `provider` 为空字符串的记录，如果 `model` 也为空或 `unknown`，则统一标记为 `unknown` / `unknown`，避免前端展示为空白或 `-`。

## 架构设计

无需引入新架构或新依赖。所有修改均为现有服务内部的局部逻辑调整：

- `DashboardService`：统计口径变更（消息维度 → metadata JSON 解析）
- `StateGraphReActAgent` / `StateGraphPlanExecuteAgent`：事件发送条件放宽
- `TokenUsageService`：查询过滤条件收紧 + 空值兜底

## 关键代码结构

无需新增接口或类型定义，仅修改现有方法内部逻辑。

## 实现注意事项

- **兼容性**：`DashboardService` 的 metadata JSON 解析需做好异常防护（try-catch），避免某条消息格式异常导致整个统计失败。
- **性能**：`DashboardService` 先通过 `LIKE '%"toolCalls"%'` 在数据库层过滤，仅对少量命中消息做 JSON 解析，性能开销可控。
- **历史数据**：修复 2a 和 2b 只能解决新消息的统计问题，历史已保存的无 provider 消息会自然被修复 2b 的查询条件排除，不再出现在 Token 统计列表中。
- **Blast radius**：`_usage_final` 事件的消费者仅有 `StreamAccumulator`，其 `accept` 方法已支持处理 token 为 0 的场景，放宽发送条件不会引入副作用。