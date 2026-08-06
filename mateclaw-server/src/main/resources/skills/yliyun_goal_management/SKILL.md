---
name: yliyun_goal_management
version: "1.0.0"
description: "目标管理技能：帮助理解项目目标、拆解任务、规划执行方案、复盘进展，并通过提案机制向 Human 提交变更建议。"
tags:
  - goal
  - planning
  - task-management
  - proposal
  - review
dependencies:
  tools:
    - goal.get_project_context
    - goal.get_project
    - goal.submit_proposal
    - goal.start_task_execution
    - goal.submit_task_result
    - goal.attach_evidence
---

# 目标管理技能

## 何时使用

当用户需要管理项目目标、规划任务、追踪执行进度或复盘分析时使用。

### 应该使用
- 用户询问项目目标、任务或进度
- 用户需要规划新目标或调整现有目标
- 用户需要拆解目标为具体任务
- 用户需要复盘项目进展
- 用户需要 AI 帮助分析阻塞点和风险

### 不应使用
- 纯文件操作（使用云盘助手）
- 纯代码开发（使用编程助手）
- 只需简单问答无需操作 Goal 系统

## 工作流程

### 第一步：理解现状

1. 使用 `goal.get_project_context` 获取项目完整上下文
2. 分析目标层次结构、任务状态分布、成员角色
3. 识别阻塞任务、过期任务、未验收任务

### 第二步：分析或规划

根据用户意图选择：

**A. 状态查询**：直接展示项目现状，突出关键指标
- 目标完成率
- 任务状态分布（READY/IN_PROGRESS/BLOCKED/SUBMITTED）
- 待验收任务数量
- 风险提示

**B. 目标规划**：
1. 理解用户的目标意图和时间范围
2. 分析当前目标体系的覆盖和缺口
3. 生成 Goal Plan Proposal（通过 `goal.submit_proposal`，type=GOAL_PLAN）
4. Proposal 内容应包含：
   - 建议新增/调整的目标和关键结果
   - 每个目标的优先级和权重
   - 与现有目标的关联关系
   - 预期的时间线和里程碑

**C. 任务拆解**：
1. 获取目标详情
2. 根据目标拆解为可执行的任务
3. 为每个任务建议：
   - executorType：HUMAN（人工执行）或 AGENT（AI 执行）
   - 优先级和预估工时
   - 验收标准
4. 生成 Task Plan Proposal

**D. 进展复盘**：
1. 获取项目当前状态
2. 分析已完成 vs 计划中的差异
3. 识别阻塞原因和影响
4. 生成复盘报告和建议

### 第三步：提交提案

所有变更建议必须通过 `goal.submit_proposal` 提交：
- proposalType：GOAL_PLAN（目标规划）或 TASK_RESULT（任务结果）
- payloadJson：结构化的变更内容
- 提案提交后状态变为 PENDING_CONFIRMATION，等待 Human 确认

### 第四步：任务执行（仅限 AGENT 类型任务）

对于 executorType=AGENT 的任务：
1. 使用 `goal.start_task_execution` 标记开始执行
   - 传入 taskId、tenantId、workspaceId
2. 执行任务工作
3. 使用 `goal.submit_task_result` 提交结果
   - status：SUCCEEDED 或 FAILED
   - summary：执行摘要
   - deliverables：交付物列表
4. 使用 `goal.attach_evidence` 附加执行证据

## 提案格式规范

### GOAL_PLAN 提案

```json
{
  "title": "Q3 目标规划",
  "summary": "基于当前项目进展，建议新增以下季度目标...",
  "goals": [
    {
      "title": "提升用户活跃度",
      "description": "通过优化推荐算法和推送策略，提升 DAU 30%",
      "goalType": "OBJECTIVE",
      "priority": "HIGH",
      "keyResults": [
        { "title": "推荐算法 CTR 提升 20%", "metricType": "PERCENTAGE", "targetValue": 20, "unit": "%" },
        { "title": "推送打开率提升 15%", "metricType": "PERCENTAGE", "targetValue": 15, "unit": "%" }
      ]
    }
  ],
  "risks": ["资源有限可能影响进度", "算法优化需要数据积累期"],
  "timeline": "2026-Q3"
}
```

### TASK_RESULT 提案

```json
{
  "title": "竞品分析报告完成",
  "summary": "已完成对 Top 5 竞品的功能对比分析",
  "deliverables": ["竞品分析报告.md", "功能对比矩阵.xlsx"],
  "findings": "主要发现：...",
  "recommendations": "建议：..."
}
```

## 安全规则

- 不直接修改目标、任务状态（必须通过 MCP Tool）
- 不自行验收任务（这是 Human 的职责）
- 遇到超出能力范围的决策，生成提案请求 Human 决策
- 不访问或引用未授权的项目数据
- 提案中不包含敏感信息（密钥、密码、个人隐私）
