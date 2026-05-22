# UI 精简改动 - 验证测试用例

> 基于 MateClaw 内置的 3 个 Agent、19 个工具、31 条 Guard 规则和审批工作流设计。
> 默认登录：admin / admin123

---

## 前置条件

1. 后端启动：`cd mateclaw-server && mvn spring-boot:run`（需设置 `DASHSCOPE_API_KEY`）
2. 前端启动：`cd mateclaw-ui && pnpm dev`
3. 访问 http://localhost:5173，登录

---

## 一、StreamLoadingBar 状态简化验证

### TC-1.1 思考中状态（Thinking）
- **Agent**: MateClaw Assistant（ReAct）
- **操作**: 发送 "请分析一下量子计算的发展趋势"
- **预期**:
  - 加载条显示 **"思考中…"** 和 ◐ 图标，不再显示 "准备上下文"/"读取记忆"/"推理中" 等内部阶段
  - 无 statusDetail 第二行解释文本
  - 无 slowHint（即使等待超过 8 秒也不出现 "耗时较长" 提示）
  - 仅显示耗时计时器（如 "12s"），不显示 token 计数

### TC-1.2 执行中状态（Working）
- **Agent**: MateClaw Assistant（ReAct），绑定 WebSearch 工具
- **操作**: 发送 "搜索一下今天的科技新闻"
- **预期**:
  - 工具调用时加载条切换为 **"执行中…"** 和 ⚙ 图标
  - 显示工具名（如 `search`）
  - 不显示 "正在执行工具" 的详情文本

### TC-1.3 撰写中状态（Writing）
- **Agent**: MateClaw Assistant（ReAct）
- **操作**: 发送 "写一篇 500 字的短文"
- **预期**:
  - 内容流输出阶段，加载条显示 **"生成中…"** 和 ▸ 图标
  - 流结束后加载条消失

### TC-1.4 错误/中断状态
- **操作**: 发送消息后立即点击停止按钮
- **预期**:
  - 加载条图标变为 ⊘，文本变红
  - 无 amber/blue 等其他颜色状态

---

## 二、审批 UI 精简验证

### TC-2.1 Shell 命令触发审批（高危操作）
- **Agent**: MateClaw Assistant（ReAct），绑定 Shell 工具
- **操作**: 发送 "帮我删除 /tmp/test 目录下的所有临时文件"
- **预期**:
  - Agent 推理后调用 `execute_shell_command`，参数含 `rm`
  - Guard 规则 `SHELL_RM` 触发 → 进入审批流程
  - **ChatInput 区域**：替换为审批栏，显示工具名 + 批准/拒绝按钮（保留）
  - **MessageBubble 中**：仅显示一行 "等待审批：`execute_shell_command`"，无完整的审批卡片（无 severity 徽章、无 findings 列表、无参数展示、无等待 spinner）
  - 点击"批准"后，气泡状态变为 "已批准：`execute_shell_command`"

### TC-2.2 文件写入触发审批
- **Agent**: MateClaw Assistant（ReAct），绑定 WriteFile 工具
- **操作**: 发送 "创建一个 hello.txt 文件，内容写 Hello World"
- **预期**:
  - `write_file` 工具触发审批
  - 输入栏显示审批操作，气泡仅一行状态
  - 拒绝后，气泡状态变为 "已拒绝：`write_file`"

### TC-2.3 危险命令直接阻断（CRITICAL 级别）
- **Agent**: MateClaw Assistant（ReAct），绑定 Shell 工具
- **操作**: 发送 "执行 rm -rf /"
- **预期**:
  - Guard 规则 `SHELL_RM_RF_ROOT` 直接 BLOCK
  - 不进入审批流程，直接返回阻断消息
  - 输入栏不显示审批栏

---

## 三、Plan-Execute 流程验证

### TC-3.1 PlanStepsPanel 渲染唯一性
- **Agent**: Task Planner（Plan-Execute）
- **操作**: 发送 "帮我调研 MateClaw 项目的技术栈，列出前端和后端分别用了哪些核心技术，然后生成一个技术概览文档"
- **预期**:
  - 生成计划后，PlanStepsPanel 只在消息气泡中出现**一次**
  - 步骤进度正确显示（pending → running → completed）
  - 不在分段式视图和传统模式中同时出现两个 PlanStepsPanel

### TC-3.2 Plan 中触发审批的步骤暂停与恢复
- **Agent**: Task Planner（Plan-Execute），绑定 Shell + WriteFile 工具
- **操作**: 发送 "查看当前目录结构，然后创建一个 project-summary.md 文件"
- **预期**:
  - 计划包含多个步骤
  - 涉及文件写入的步骤触发审批
  - 审批期间，PlanStepsPanel 该步骤显示 running 状态
  - 气泡中审批为一行极简文本
  - 批准后步骤继续执行，状态更新为 completed

---

## 四、BrowserTimeline 默认收起验证

### TC-4.1 浏览器操作时间线默认折叠
- **Agent**: MateClaw Assistant（ReAct），绑定 BrowserUse 工具
- **操作**: 发送 "打开浏览器访问 baidu.com，截图"
- **预期**:
  - 浏览器操作完成后，时间线默认**收起**
  - 仅显示标题栏 "Browser: N actions"
  - 点击标题栏可展开查看操作细节和截图

---

## 五、动画与视觉一致性验证

### TC-5.1 无 Typing Bounce Dots
- **Agent**: 任意 Agent
- **操作**: 发送消息，观察 AI 响应开始前
- **预期**:
  - 不再出现三个弹跳圆点的加载动画
  - 使用 TypingCursor（闪烁光标）代替

### TC-5.2 动画一致性
- **操作**: 在不同场景触发加载状态
- **预期**:
  - StreamLoadingBar 的 icon-pulse 动画时长统一为 1.2s
  - 所有 spinner 使用相同的旋转动画
  - 无竞争性的多重脉冲动画

---

## 六、ChatInput 占位符验证

### TC-6.1 加载中占位符不暴露键盘操作
- **Agent**: 任意 Agent
- **操作**: 发送消息，在 AI 生成过程中观察输入框
- **预期**:
  - 占位符仅显示原始 placeholder 文本
  - 不再显示 "(Enter to send / interrupt)"
  - 发送按钮变为红色停止图标已足够提示

---

## 七、深色模式主题变量验证

### TC-7.1 深色模式颜色正确性
- **操作**: 切换到深色模式（侧边栏底部主题切换）
- **检查项**:
  - StreamLoadingBar 文本颜色使用主题主色调（非硬编码 #f97316）
  - 审批状态文字颜色正确（成功绿/失败红均跟随主题）
  - 中断按钮使用 `--mc-warning` 变量的深色模式值 (#fbbf24)
  - 排队指示器使用 `--mc-info` 变量的深色模式值 (#60a5fa)
  - 工具调用状态图标（成功/失败/等待）颜色均来自 CSS 变量

### TC-7.2 浅色/深色快速切换
- **操作**: 在生成过程中快速切换浅色/深色模式
- **预期**:
  - 所有颜色即时切换，无残留的硬编码颜色

---

## 八、回归测试

### TC-8.1 普通对话流程（无工具调用）
- **Agent**: MateClaw Assistant
- **操作**: 发送 "你好，介绍一下你自己"
- **预期**: 正常生成回复，无 UI 异常

### TC-8.2 多轮对话 + 工具调用
- **Agent**: MateClaw Assistant，绑定多个工具
- **操作**: 连续发送 3-5 条消息，触发不同工具
- **预期**: 每轮消息的加载条、工具调用显示、内容输出均正常

### TC-8.3 消息中断与重试
- **操作**: 发送消息 → 中断 → 重试
- **预期**: 中断指示器正常显示，重试后正常生成

### TC-8.4 会话切换
- **操作**: 在多个会话间切换
- **预期**: 历史消息正确加载，审批状态（已批准/已拒绝）正确回显

### TC-8.5 移动端响应式
- **操作**: 浏览器宽度缩小到 768px 以下
- **预期**:
  - 审批栏在 ChatInput 中正常自适应
  - 气泡中审批状态一行文本不溢出
  - 加载条内容不截断

---

## 九、Project 模块验证清单

> 当前正确使用流程：**先进入聊天页并选定 Agent → 新建会话 → 在 Project 菜单中通过可点击目录浏览器选择工作目录 → 发送首条消息**。
> 也允许“不先点新对话，直接先设置 Project 再发送首条消息”，但**不要先设置 Project 再点击新对话**，否则本地暂存的 project 状态会被重置。

### TC-9.0 Workspace / Project 入口可见性
- **操作**:
  1. 打开聊天页，观察输入框下方入口区
  2. 当前会话未绑定 project 时观察按钮文案
  3. 绑定一个 project 后再次观察
- **预期**:
  - workspace 与 project 是两个独立入口
  - workspace 入口始终显示当前 workspace 名称
  - 未绑定 project 时，不再显示“工作区根目录”这种默认 project 标签
  - 未绑定时 project 入口显示“设置 Project”之类明确文案
  - 绑定后 project 入口显示实际 project 相对路径

### TC-9.1 新会话设置 Project 边界（推荐流程）
- **前置条件**:
  - 当前 workspace 已配置 `basePath`
  - `basePath` 下存在一个可访问子目录，例如 `mateclaw-ui/src/components`
- **操作**:
  1. 打开聊天页并选择任意 Agent
  2. 点击“新对话”
  3. 点击输入区下方的 Project 芯片，展开 Project 菜单
  4. 在目录浏览器中逐级点击进入 `mateclaw-ui/src/components`
  5. 点击“使用当前目录”，再点击“保存”
  6. 发送“请列出当前目录下的组件文件”
- **预期**:
  - 首条消息发送成功
  - 会话被创建后，Project 边界落在所选子目录，而不是 workspace root
  - 回复中的文件读取 / 列举范围围绕该子目录展开
  - 右侧 Project Changes 面板中的当前 Project 显示为该目录

### TC-9.2 未手动新建会话，先选 Project 再发送首条消息
- **操作**:
  1. 打开聊天页并选择任意 Agent
  2. 不点击“新对话”
  3. 点击输入区下方的 Project 芯片
  4. 在目录浏览器中点击进入某个 workspace 子目录
  5. 点击“使用当前目录”，再点击“保存”
  6. 发送“读取当前 project 下的 README 或入口文件”
- **预期**:
  - UI 提示该目录将在首条消息发送时应用
  - 首条消息发送时自动创建 conversation
  - 后端按该 Project 边界执行，而不是退回 workspace root
  - 刷新页面或重新进入该会话后，Project 仍能正确回显

### TC-9.3 错误流程验证：先选 Project 再点击“新对话”
- **操作**:
  1. 在当前聊天页先通过目录浏览器设置一个 workspace 子目录作为 Project
  2. 随后点击“新对话”
  3. 观察 Project 显示，再发送一条读取目录的消息
- **预期**:
  - 新对话会重置本地暂存 Project 状态
  - 当前 Project 显示回到 workspace root 或默认状态
  - 发送消息后不会继续沿用刚才未持久化的旧 Project
  - 该行为与当前代码实现一致，应记录为“现状限制 / 使用注意事项”

### TC-9.4 已存在会话切换后 Project 正确回显
- **操作**:
  1. 准备两个会话 A / B，分别绑定不同子目录
  2. 在侧边栏来回切换 A / B
- **预期**:
  - 会话列表中每条会话都显示轻量 Project 标签
  - 切换到 A 时，Project 回显为 A 绑定的目录
  - 切换到 B 时，Project 回显为 B 绑定的目录
  - 不会出现会话间 Project 串用
  - `@project`、Project 芯片、Project Changes 面板三处显示保持一致

### TC-9.4a 会话列表 Project 标签
- **操作**:
  1. 准备一个绑定 workspace root 的会话和一个绑定子目录的会话
  2. 观察左侧会话列表
- **预期**:
  - 会话标题下方显示轻量 Project 标签
  - 使用 workspace root 的会话显示“工作区根目录”
  - 使用子目录的会话显示相对路径，而不是冗长绝对路径
  - 超长路径会省略显示，但 hover 可看到完整值

### TC-9.5 更新现有会话的 Project 边界
- **操作**:
  1. 进入一个已有会话
  2. 将 Project 从 workspace root 切到某个子目录
  3. 发送“在当前 project 下搜索某个组件 / 文件”
- **预期**:
  - 若该会话已经有消息记录，保存前会弹出风险提示，提醒“建议新建会话再切换 Project”
  - 弹窗里提供“新建会话并切换”快捷按钮
  - 用户取消后，不应修改当前 Project
  - 保存后提示“工作目录已更新”
  - 后续消息按新目录执行
  - Review / Project Changes 面板中的 project 路径同步更新

### TC-9.5b 风险提示中的快捷分流
- **操作**:
  1. 在一个已有多轮消息的会话中切换 Project
  2. 在提示框里点击“新建会话并切换”
- **预期**:
  - 当前消息历史被保留在原会话中
  - UI 进入一个新的空会话
  - 新会话的 Project 已切换到目标目录
  - 首条消息发送时按新 Project 生效

- **操作**:
  1. 在同样场景下再次切换 Project
  2. 在提示框里点击“仍然切换”
- **预期**:
  - 继续使用当前会话
  - 当前会话的 Project 被更新
  - 属于“允许但不推荐”的操作路径

### TC-9.5a 点击式目录浏览器交互
- **操作**:
  1. 点击输入区下方 Project 芯片
  2. 在目录浏览器中点击某个子目录
  3. 尝试使用“回到根目录”“上一级”“使用当前目录”
- **预期**:
  - 点击子目录后可继续向下浏览
  - “回到根目录”会回到 workspace root
  - 非根目录时“上一级”可用，根目录时禁用
  - “使用当前目录”会把当前浏览目录写入工作目录输入框

### TC-9.6 Project 重置为 workspace root
- **操作**:
  1. 进入一个已绑定子目录的会话
  2. 点击 Project 菜单中的“重置”
  3. 再发送一条目录扫描消息
- **预期**:
  - Project 恢复为 workspace root
  - projectRelativePath 回显为“工作区根目录”语义
  - 后续文件范围扩展回整个 workspace

### TC-9.7 越界目录阻断
- **操作**:
  1. 在 Project 输入框中填写一个 workspace 之外的绝对路径
  2. 点击保存
- **预期**:
  - 后端拒绝保存
  - 错误信息明确说明目录必须位于当前工作区活动目录内
  - 当前会话 Project 保持不变

### TC-9.8 不存在目录 / 非目录阻断
- **操作**:
  1. 输入一个不存在的路径保存
  2. 输入一个实际文件路径而不是目录保存
- **预期**:
  - 分别返回“目录不存在” / “目标不是目录”类错误
  - 当前 Project 不被污染

### TC-9.9 符号链接逃逸阻断
- **前置条件**:
  - workspace 内存在一个指向 workspace 外部的符号链接目录（若运行环境支持）
- **操作**:
  1. 尝试把该符号链接目录设置为 Project
- **预期**:
  - 后端拒绝，并提示目录通过符号链接越过工作区边界
  - Project 保持原值

### TC-9.10 Slash Commands 与 Mentions 的 Project 一致性
- **操作**:
  1. 进入一个已绑定 Project 的会话
  2. 在输入框使用 `/project`
  3. 输入 `@project`
- **预期**:
  - `/project` 返回的 workspace / project 信息与当前会话一致
  - `@project` 引用的是当前会话 Project，而不是其他会话或全局路径

### TC-9.11 Project Changes 面板 - Git 场景
- **前置条件**:
  - 当前 Project 位于 Git 仓库内
  - 目录内已有若干 modified / untracked 文件
- **操作**:
  1. 打开聊天页右上角的 Project Changes 面板
- **预期**:
  - 面板显示当前 Project 路径
  - 显示按 `added / modified / deleted / renamed / untracked` 聚合的摘要
  - 显示当前 Project 变更文件列表
  - 文件状态标签与 Review 面板中的含义一致

### TC-9.12 Project Changes 面板 - 非 Git 场景回退
- **前置条件**:
  - 当前 Project 不在 Git 仓库内
  - Agent 本轮通过 `write_file` / `edit_file` 改动了文件
- **操作**:
  1. 打开 Project Changes 面板
- **预期**:
  - 面板仍可展示“最近一次回复改动”和最近 review 历史
  - 若拿不到 Git 快照，不应报错或空白崩溃
  - 当前实现允许“真实 project changed-files 快照缺失，仅展示工具写入记录”

### TC-9.13 Review 与独立侧边面板的一致性
- **操作**:
  1. 在当前 Project 下让 Agent 新增 / 修改文件
  2. 查看对应 assistant 消息里的 Review 折叠区
  3. 再打开 Project Changes 侧边面板
- **预期**:
  - 本轮改动文件在两处都能看到
  - 状态标签、数量统计、Project 路径语义一致
  - 独立侧边面板额外展示最近 review 历史和当前 project 聚合视图

### TC-9.14 Checkpoint 状态表达
- **操作**:
  1. 打开任意一条带 Review 的消息
  2. 再打开 Project Changes 侧边面板
- **预期**:
  - 两处都能看到 Checkpoint 状态说明
  - 当前明确显示“不支持完整 restore checkpoint”
  - 不会误导用户认为已有完整项目快照恢复能力

### TC-9.15 移动端 Project Changes 抽屉
- **操作**:
  1. 将浏览器宽度缩小到 768px 以下
  2. 打开聊天页头部的 Project Changes 按钮
- **预期**:
  - 面板以右侧抽屉形式弹出
  - 背景出现遮罩
  - 点击遮罩或关闭按钮可正常收起
  - 面板内容不溢出、不遮挡输入主操作区
