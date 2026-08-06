---
name: yliyun_goal_worker
version: "1.0.0"
description: "目标执行工作技能：Agent Worker 执行具体任务、提交交付物、报告进展和阻塞。仅可访问当前执行任务的上下文。"
tags:
  - goal
  - worker
  - execution
  - task
dependencies:
  tools:
    - goal.get_project_context
    - goal.start_task_execution
    - goal.submit_task_result
    - goal.attach_evidence
---

# 目标执行工作技能

## 何时使用

当 Agent Worker 被分配执行 Goal 系统中的 AGENT 类型任务时使用。

### 应该使用
- 被委派执行具体的 Goal 业务任务
- 需要报告任务进展或阻塞
- 需要提交任务执行结果和交付物
- 需要附加执行证据到任务

### 不应使用
- 规划目标或拆解任务（这是 Goal Assistant 的职责）
- 验收任务（这是 Human 的职责）
- 修改项目目标或范围
- 访问其他任务的上下文

## 工作流程

### 第一步：获取任务上下文

1. 使用 `goal.get_project_context` 获取项目上下文
2. 定位到自己的任务（通过 taskId）
3. 理解任务的验收标准和约束条件

### 第二步：开始执行

1. 使用 `goal.start_task_execution` 标记任务开始
   ```
   goal.start_task_execution({
     taskId: <任务ID>,
     tenantId: <租户ID>,
     workspaceId: <工作空间ID>,
     runId: <当前 Run ID>
   })
   ```
2. 任务状态从 READY → IN_PROGRESS

### 第三步：执行工作

根据任务类型执行具体工作：
- 研究分析类：搜索资料、分析数据、生成报告
- 文档处理类：读取文件、整理内容、生成文档
- 数据处理类：提取信息、转换格式、统计分析
- 其他类型：根据任务描述执行

### 第四步：提交结果

1. 使用 `goal.submit_task_result` 提交执行结果
   ```
   goal.submit_task_result({
     taskId: <任务ID>,
     tenantId: <租户ID>,
     status: "SUCCEEDED",  // 或 "FAILED"
     summary: "执行摘要...",
     deliverables: "[\"file1.md\", \"file2.xlsx\"]"
   })
   ```
2. 任务状态从 IN_PROGRESS → SUBMITTED
3. **注意：提交后任务等待 Human 验收，Worker 不能自行验收**

### 第五步：附加证据

1. 使用 `goal.attach_evidence` 附加执行证据
   ```
   goal.attach_evidence({
     taskId: <任务ID>,
     tenantId: <租户ID>,
     evidenceType: "FILE",  // FILE/SUMMARY/LINK
     fileId: "文件ID",
     content: "证据描述"
   })
   ```

## 报告阻塞

如果任务执行中遇到阻塞：

1. 使用 `goal.submit_task_result` 提交 FAILED 状态
2. 在 summary 中详细说明阻塞原因
3. 通过提案机制向 Human 报告（由 Goal Assistant 处理）

## 执行约束

- 只能访问当前分配任务的上下文
- 不能修改项目目标或其他任务
- 不能自行验收任务
- 不能添加项目成员
- 不能访问未授权的文件
- 执行超时或失败时，提交 FAILED 状态并说明原因

## 交付物规范

- 研究报告：包含标题、摘要、方法、发现、结论
- 数据分析：包含数据来源、处理方法、结果、可视化建议
- 文档产出：结构清晰、格式规范、引用来源
- 所有交付物通过 goal.attach_evidence 附加到任务
