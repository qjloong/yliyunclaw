# AI Sites 的 MateClaw 配置

本文件只描述 Website Assistant 和生成团队配置，不定义站点、域名、发布、表单或商机数据库。

## 配置对象

- Website Assistant：需求澄清、站点规划和对话入口。
- Generation Team：结构、文案、视觉资产、页面 DSL 候选生成。
- SEO/QA Worker：SEO、可访问性、链接、合规和 Schema 检查。
- AI Sites Tool Binding：读取授权来源、提交 Proposal/Revision Candidate、查询 Job 状态。
- Artifact Callback：回传候选产物、校验结果和运行轨迹。

## 配置原则

- MateClaw 不直接激活发布 Revision，也不调用 Site Delivery 公网管理 API。
- Human Lock 内容作为不可覆盖约束进入执行上下文。
- 生成结果必须通过云盘 AI Sites Schema 校验与审批后才成为正式 Revision。
- Team/Skill/Prompt/模型配置均需版本化并记录到 Run Trace。
