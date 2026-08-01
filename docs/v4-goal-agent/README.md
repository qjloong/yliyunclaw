# 一粒云 Goal Agent 高保真 HTML 原型

## 文件说明

- `goal-agent-prototype.html`：可直接双击打开的单页高保真原型，无外部依赖。
- `goal-agent-mock-data.json`：完整 Mock 数据，可在“Agent 设置 → 数据维护”中导入。
- `goal-agent-data-model.md`：核心实体、状态机、验收和对账逻辑说明。

## 已覆盖功能

1. 今日工作台与五类工作板块
2. 年、季度、月、周多级目标树
3. 任务列表与看板切换
4. 目标自动拆解和规划校验
5. 验收标准、证据与验收评分
6. 每日计划与实际工作对账
7. 周燃尽、工时、目标贡献等图表
8. 个人空间资料关系与母版复用建议
9. Goal Agent 对话与模拟规划建议
10. 调度、文件权限、JSON 导入导出
11. 浏览器 `localStorage` 状态保存

## 运行方式

直接用 Chrome、Edge 或其他现代浏览器打开 `goal-agent-prototype.html`。

原型数据默认固定在 2026-07-29，用于完整展示日结、今日计划和周度趋势。页面顶部日期使用浏览器当前日期。

## 产品逻辑说明

- 任务只有在“验收通过”后才正式计入目标进度。
- 任务通过 `goalId`、`projectId`、`boardId` 建立多维归属。
- 验收得分由 `acceptanceRules[].score` 按通过状态加权计算。
- 对账比较计划任务、实际任务、有效验收、工时、临时任务和延期任务。
- 文件通过 `sourceId`、`downstream[]` 和任务关联形成资料复用图。
