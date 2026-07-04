# Agent Template A — 交易智能体（Trading Agent）设计方案

> 基于 MateClaw 项目架构分析，结合现有 Agent 模板体系、工具注册机制、RulePack/Skill 系统、知识库管线与审批流程，拓展一个面向金融交易场景的智能体。

---

## 一、项目架构回顾与分析结论

### 1.1 MateClaw Agent 体系核心设计模式

通过与 Teacher Agent（最成熟案例）和 Coding Agent 的对比分析，MateClaw 的 Agent 扩展遵循以下模式：

```
Template JSON 定义
  → AgentGraphBuilder 构建 StateGraph
    → 注入 Prompt（系统提示词 + RulePack 规则摘要 + 知识库上下文）
    → 注入 ToolSet（@Tool Bean + MCP + Plugin）
    → 注入 Agent Profile（角色、语气、UX）
  → StateGraphReActAgent / StateGraphPlanExecuteAgent 执行
    → Reasoning → Action → Observation → Final Answer
```

### 1.2 现有可复用能力矩阵

| 能力层 | 现有实现 | 交易 Agent 复用度 |
|--------|---------|------------------|
| **数据源连接** | `DatasourceTool` + `SqlQueryTool` — 已支持外部数据库只读查询 | ★★★★★ 直接复用 |
| **定时任务** | `CronJobTool` + `mate_cron_job` 表 — 支持 cron 表达式调度 | ★★★★☆ 可复用调度框架 |
| **Web 搜索** | `WebSearchTool` — 支持搜索引擎检索 | ★★★★☆ 用于市场资讯、公告抓取 |
| **浏览器自动化** | `BrowserUseTool` + `FileTypeDetectorTool` | ★★★☆☆ 用于爬取财经网站数据 |
| **知识库（Wiki）** | 完整的多级知识库体系：raw material → page → chunk → embedding | ★★★★★ 存储交易策略、研报、法规 |
| **RulePack** | Teacher Agent 的 RulePack 系统：JSON 规则包 + 工作区/全局覆盖 | ★★★★★ 交易风控规则的天然载体 |
| **Skill 系统** | 声明式技能定义 + 绑定 + 运行时注入 | ★★★★☆ 交易执行流程编排 |
| **审批流程** | 完整的审批卡系统（一次/会话/工作区范围） | ★★★★★ 交易执行审批的天然基础 |
| **Harness 验收** | 规则驱动验收 + 自优化草案 | ★★★★☆ 策略回测验收 |
| **Plugin 系统** | 插件 SDK + Agent 绑定 | ★★★☆☆ 可扩展为数据源插件 |
| **Shell 执行** | `ShellExecuteTool` | ★★★☆☆ 用于运行量化脚本 |
| **文件操作** | `ReadFileTool` / `WriteFileTool` / `EditFileTool` | ★★★☆☆ 分析报告生成 |

### 1.3 关键缺失能力

| 能力 | 缺失程度 | 实现优先级 |
|------|---------|-----------|
| **实时行情数据** | 完全缺失 — 需要接入行情 API（如 Tushare、AKShare、东方财富等） | P0 |
| **技术指标计算** | 缺失 — 需要在 Java 侧或 Python 侧实现常用指标（MA、MACD、RSI、布林带等） | P0 |
| **策略回测引擎** | 缺失 — 需要引入回测框架（如 backtrader.py 或自建） | P1 |
| **交易执行接口** | 缺失 — 如需实盘需对接券商 API（极其谨慎） | P2（建议仅模拟） |
| **仓位/组合管理** | 缺失 — 需要新建数据模型 | P1 |

---

## 二、交易 Agent 价值评估

### 2.1 核心价值主张

| 维度 | 价值描述 |
|------|---------|
| **信息聚合** | 将多源市场数据（行情、新闻、财报、公告、研报）自动聚合为可操作的洞察 |
| **规则化风控** | 利用 RulePack 系统固化交易纪律，避免情绪化决策 |
| **可追溯决策** | 每次交易建议都关联到具体的分析依据和数据来源 |
| **知识沉淀** | 交易策略、经验教训、市场规律可持续积累到知识库 |
| **安全边界** | 交易执行必须经审批，支持一次/会话/工作区三级审批范围 |
| **多市场覆盖** | 同一套架构可支持 A 股、港股、美股、期货、加密货币 |

### 2.2 目标用户画像

| 用户类型 | 使用场景 | 核心需求 |
|---------|---------|---------|
| **独立交易者** | 盘前分析 → 盘中监控 → 盘后复盘 | 行情聚合、信号生成、风险提醒 |
| **量化研究员** | 策略开发 → 回测 → 参数优化 | 数据获取、回测引擎、结果分析 |
| **投资团队** | 投研 → 决策 → 执行 → 复盘 | 协作审批、知识共享、业绩归因 |
| **学习用户** | 模拟交易 → 策略学习 → 市场认知 | 教育引导、模拟环境、风险提示 |

### 2.3 风险与边界

> ⚠️ **核心原则：本 Agent 是决策辅助工具，不是自动交易机器人。实盘交易必须经过人工审批。**

| 风险 | 缓解措施 |
|------|---------|
| 错误交易信号 | RulePack 多层校验 + 置信度评分 + 人工确认 |
| 数据延迟/错误 | 多数据源交叉验证 + 数据新鲜度标记 |
| 过度拟合策略 | Harness 回测验收 + 样本外测试 |
| 合规风险 | 法规知识库 + 交易限制规则 |
| 系统故障 | 交易指令幂等 + 超时保护 + 状态回滚 |

---

## 三、可行性分析

### 3.1 技术可行性：★★★★☆（高）

- MateClaw 的 StateGraph 架构天然支持交易 Agent 的"分析→决策→执行→复盘"多阶段流程
- 数据源连接、定时任务、审批流、知识库均已就绪
- 主要新增工作量在行情数据接入和技术指标计算
- 可复用 Teacher Agent 的 RulePack 系统用于风控规则

### 3.2 数据可行性：★★★★☆（高）

- 国内：Tushare Pro、AKShare、东方财富 API 均有成熟 Python/Java SDK
- 国外：Yahoo Finance、Alpha Vantage、Polygon.io
- MateClaw 已支持 DatasourceTool 连接外部数据库，可将行情数据存入 MySQL/PostgreSQL 后查询

### 3.3 合规可行性：★★★☆☆（中）

- 需注意金融数据使用的合规性（如交易所行情数据授权）
- 实盘交易接口对接需要相应牌照（建议仅做模拟交易）
- 投资建议类输出需要风险提示

### 3.4 总体结论：**可行，建议分三阶段渐进式落地**

---

## 四、设计方案

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Trading Agent 架构                        │
├─────────────────────────────────────────────────────────────┤
│  Template (trading-agent.json)                               │
│  ├── Agent Profile: 交易分析师                               │
│  ├── Agent Type: plan_execute（Plan-to-Trade 模式）          │
│  ├── Capability Pack: 金融市场分析能力包                      │
│  ├── RulePacks: 风控规则 + 策略规则 + 合规规则                │
│  └── Skills: 盘前分析 / 盘中监控 / 盘后复盘 / 策略回测         │
├─────────────────────────────────────────────────────────────┤
│  Tools Layer                                                 │
│  ├── market_quote       行情查询（新增）                      │
│  ├── market_indicator   技术指标计算（新增）                   │
│  ├── market_news        市场资讯聚合（新增）                   │
│  ├── market_screener    条件选股（新增）                       │
│  ├── portfolio_analyze  持仓分析（新增）                       │
│  ├── datasource_exec    SQL 数据查询（复用）                   │
│  ├── web_search         网络搜索（复用）                       │
│  ├── browser_use         浏览器抓取（复用）                     │
│  └── cron_schedule      定时任务调度（复用）                   │
├─────────────────────────────────────────────────────────────┤
│  StateGraph (Plan-to-Trade)                                  │
│  START → ANALYSIS → PLAN → EXECUTION(_WITH_APPROVAL) →       │
│  MONITORING → REVIEW → END                                   │
├─────────────────────────────────────────────────────────────┤
│  Data Layer                                                  │
│  ├── 行情数据库（MySQL/PostgreSQL，通过 DatasourceTool 连接） │
│  ├── 知识库（Wiki KB）：策略文档、研报、法规                    │
│  ├── CronJob：定时拉取行情、触发监控                          │
│  └── Memory：用户偏好、自选股、常用策略                       │
├─────────────────────────────────────────────────────────────┤
│  Safety Layer                                                │
│  ├── TradeApprovalGuard：交易指令审批门控                     │
│  ├── RiskRulePack：仓位限制、止损线、最大回撤                  │
│  └── ComplianceCheck：合规检查（如 A 股 T+1 规则）            │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 模板定义 (`trading-agent.json`)

```json
{
  "id": "builtin.trading_agent",
  "name": "Trading Agent",
  "nameZh": "交易智能体",
  "version": "1.0.0",
  "status": "published",
  "category": "finance",
  "domain": "securities_trading",
  "visibility": "builtin",
  "ownerType": "system",
  "description": "A financial trading analysis agent supporting market data aggregation, technical analysis, strategy backtesting, portfolio monitoring, and trade decision support with mandatory human approval for execution.",
  "descriptionZh": "金融市场交易分析智能体，支持行情数据聚合、技术分析、策略回测、持仓监控和交易决策辅助，交易执行必须经人工审批。",
  "icon": "📈",
  "agentType": "plan_execute",
  "tags": "finance,trading,stock,market,analysis,portfolio",
  "featured": false,
  "sortOrder": 20,
  "maxIterations": 15,

  "homeSubtitle": "金融市场交易分析助手",
  "homeQuickStarts": [
    {
      "title": "了解我的分析能力",
      "prompt": "请介绍你作为交易智能体能做什么，适合分析哪些市场，需要我提供哪些信息。"
    },
    {
      "title": "快速市场概览",
      "prompt": "请帮我分析当前 A 股市场整体状况，包括主要指数表现、市场情绪和热点板块。"
    },
    {
      "title": "分析一只股票",
      "prompt": "请从技术面、基本面和市场情绪三个维度分析我关注的股票，并给出风险提示。"
    },
    {
      "title": "构建一个监控任务",
      "prompt": "帮我创建一个定时监控任务，当某只股票触发我设定的技术指标条件时提醒我。"
    }
  ],

  "runtime": {
    "preferredMode": "plan_execute",
    "allowedModes": ["react", "plan_execute"],
    "requirePlanBeforeExecute": true,
    "requireRiskDisclosure": true,
    "requireDataSourceCitation": true
  },

  "permissions": {
    "usableBy": ["admin", "user"],
    "editableBy": ["admin"],
    "knowledgeBindingEditableBy": ["admin", "workspace_owner", "workspace_admin"]
  },

  "defaultWorkspacePolicy": {
    "sandboxMode": "read-only",
    "approvalPolicy": "on_trade_execute",
    "networkPolicy": "restricted",
    "allowedActions": [
      "market_data_query",
      "indicator_calculate",
      "news_aggregate",
      "portfolio_analyze",
      "report_generate",
      "strategy_backtest"
    ],
    "deniedActions": [
      "trade_execute_without_approval",
      "position_modify_without_approval",
      "market_data_write"
    ],
    "riskOverrides": {
      "trade.execute": { "riskLevel": "critical", "requiresApproval": true },
      "position.modify": { "riskLevel": "critical", "requiresApproval": true },
      "order.cancel": { "riskLevel": "high", "requiresApproval": true },
      "strategy.deploy": { "riskLevel": "high", "requiresApproval": true }
    }
  },

  "agentProfile": {
    "profileId": "trading_agent_profile",
    "displayName": "交易智能体",
    "role": "金融市场交易分析师",
    "language": "zh-CN",
    "tone": "专业、客观、审慎、数据驱动",
    "disclaimer": "本智能体提供的所有分析和建议仅供参考，不构成投资建议。投资有风险，入市需谨慎。",
    "userExperience": {
      "defaultEntry": "chat",
      "simpleModeForNormalUser": true,
      "exposeModelConfig": false,
      "exposeToolDetails": true,
      "showPlan": true,
      "showDataSource": true,
      "showRiskWarning": true
    }
  },

  "capabilityPack": {
    "packId": "capability.finance.securities_trading",
    "name": "金融市场交易分析能力包",
    "capabilities": [
      "market_data_aggregation",
      "technical_analysis",
      "fundamental_analysis",
      "sentiment_analysis",
      "portfolio_monitoring",
      "strategy_backtesting",
      "risk_management",
      "trade_journal",
      "market_screening"
    ],
    "defaultRulePackId": "trading.rulepack.risk_management.v1",
    "rulePackIds": [
      "trading.rulepack.risk_management.v1",
      "trading.rulepack.technical_strategy.v1",
      "trading.rulepack.fundamental_filter.v1",
      "trading.rulepack.compliance.v1"
    ]
  },

  "contextSources": {
    "knowledgeBases": {
      "mode": "agent_config_optional",
      "description": "交易策略、研报、法规等知识库，由用户在工作区配置。",
      "suggestedKbKinds": ["business"],
      "suggestedDomainProfileId": "finance.securities.trading"
    },
    "sessionTemporary": {
      "enabled": true,
      "priority": "highest",
      "description": "用户盘中临时上传的行情截图、分析表单等。"
    }
  },

  "tools": [
    { "name": "market_quote", "displayName": "行情查询", "type": "builtin", "riskLevel": "low", "readOnly": true, "description": "查询股票/指数/期货实时或历史行情数据" },
    { "name": "market_indicator", "displayName": "技术指标", "type": "builtin", "riskLevel": "low", "readOnly": true, "description": "计算常用技术指标：MA、MACD、RSI、KDJ、布林带等" },
    { "name": "market_screener", "displayName": "条件选股", "type": "builtin", "riskLevel": "low", "readOnly": true, "description": "根据技术面和基本面条件筛选股票" },
    { "name": "market_news", "displayName": "市场资讯", "type": "builtin", "riskLevel": "low", "readOnly": true, "description": "聚合个股公告、行业新闻、宏观经济数据" },
    { "name": "portfolio_analyze", "displayName": "持仓分析", "type": "builtin", "riskLevel": "medium", "readOnly": true, "description": "分析当前持仓组合的风险收益特征" },
    { "name": "trade_journal", "displayName": "交易日志", "type": "builtin", "riskLevel": "low", "readOnly": false, "description": "记录交易决策、执行结果和复盘笔记" },
    { "name": "strategy_backtest", "displayName": "策略回测", "type": "builtin", "riskLevel": "medium", "readOnly": true, "description": "对交易策略进行历史数据回测" },
    { "name": "web_search", "displayName": "网络搜索", "type": "builtin", "riskLevel": "low", "readOnly": true },
    { "name": "datasource_exec", "displayName": "数据查询", "type": "builtin", "riskLevel": "low", "readOnly": true, "description": "在连接的行情数据库中执行只读查询" }
  ],

  "outputFormats": ["markdown", "json", "html", "csv"],

  "qualityGates": {
    "planRequired": { "enabled": true },
    "riskDisclosure": { "enabled": true },
    "dataSourceCitation": { "enabled": true },
    "noInvestmentAdvice": { "enabled": true },
    "approvalForTrade": { "enabled": true },
    "complianceCheck": { "enabled": true }
  },

  "systemPrompt": "## Role\n你是交易智能体，一个面向金融市场的专业分析助手。你的核心职责是提供数据驱动的市场分析、策略建议和风险提示。\n\n## Core Rules\n- 你提供的是分析参考，不是投资建议。每次涉及具体标的时必须附上风险提示。\n- 所有分析结论必须基于可追溯的数据来源，数据来源可以是行情数据库、公开资讯或用户上传的资料。\n- 涉及交易执行的建议必须经过用户明确审批，不得自动执行。\n- 当用户询问\"买什么\"\"什么时候买\"等决策性问题时，应提供分析框架而非直接答案。\n- 遵守当前 RulePack 中定义的风险管理规则（仓位上限、止损比例、最大回撤等）。\n\n## Workflow\n1. 理解用户的分析目标、市场范围和约束条件。\n2. 从行情数据库、市场资讯和知识库中获取相关数据。\n3. 应用技术分析和基本面分析框架，生成结构化分析报告。\n4. 如涉及具体操作建议，先生成方案 → 用户确认 →（如需要）触发审批流程。\n5. 记录分析过程和决策依据到交易日志。\n\n## Disclaimer\n⚠️ 投资有风险，入市需谨慎。本智能体的所有输出均为基于公开数据和预设策略的机器分析，不构成任何形式的投资建议。过往业绩不代表未来表现。",

  "mockAcceptanceTasks": [
    {
      "id": "mock_001",
      "title": "市场概览分析",
      "input": "请帮我分析今天 A 股市场的整体状况，包括主要指数表现和热点板块。",
      "expected": ["展示主要指数涨跌幅", "列出热点板块", "包含风险提示", "标注数据来源"]
    },
    {
      "id": "mock_002",
      "title": "单股技术分析",
      "input": "帮我从技术面分析一下贵州茅台（600519），看看当前处于什么位置。",
      "expected": ["展示价格走势", "计算技术指标", "给出支撑/压力位", "包含风险提示"]
    },
    {
      "id": "mock_003",
      "title": "持仓风险评估",
      "input": "当前持仓包含科技股 60%、消费股 30%、现金 10%，请帮我评估组合风险。",
      "expected": ["计算行业集中度", "评估组合波动", "给出再平衡建议", "不触发交易审批"]
    }
  ]
}
```

### 4.3 核心工具设计

#### 4.3.1 `MarketQuoteTool` — 行情查询（P0）

```java
@Component
@RequiredArgsConstructor
public class MarketQuoteTool {

    private final MarketDataService marketDataService;
    private final DatasourceTool datasourceTool;

    @Tool(description = """
            查询股票/指数/期货的实时行情或历史K线数据。
            
            支持的查询类型：
            - realtime: 实时行情（最新价、涨跌幅、成交量、换手率等）
            - history: 历史K线（OHLCV + 常用指标）
            - index: 指数成分股及权重
            
            数据源：通过 DatasourceTool 连接的外部行情数据库。
            """)
    public String market_quote(
            @ToolParam(description = "证券代码，如 600519.SH / 000858.SZ / AAPL") String symbol,
            @ToolParam(description = "查询类型：realtime / history / index") String queryType,
            @ToolParam(description = "K线周期：1d / 1w / 1m，仅 history 类型需要") String period,
            @ToolParam(description = "起始日期 yyyyMMdd，仅 history 类型需要") String startDate,
            @ToolParam(description = "结束日期 yyyyMMdd，仅 history 类型需要") String endDate) {
        // 1. 通过 DatasourceTool 查询行情数据库
        // 2. 校验数据新鲜度（realtime 数据不超过 5 分钟）
        // 3. 返回结构化行情 JSON
        return "...";
    }
}
```

#### 4.3.2 `MarketIndicatorTool` — 技术指标计算（P0）

```java
@Component
public class MarketIndicatorTool {

    @Tool(description = """
            计算常用技术指标。
            
            支持指标：
            - MA(5/10/20/60/120/250): 移动平均线
            - MACD(12,26,9): 指数平滑异同移动平均线
            - RSI(6/14/24): 相对强弱指标
            - KDJ(9,3,3): 随机指标
            - BOLL(20,2): 布林带
            - ATR(14): 平均真实波幅
            - OBV: 能量潮
            - WR(10/6): 威廉指标
            
            返回各指标的最新值和历史序列。
            """)
    public String market_indicator(
            @ToolParam(description = "证券代码") String symbol,
            @ToolParam(description = "指标名称，多个用逗号分隔，如 MA,MACD,RSI") String indicators,
            @ToolParam(description = "数据周期：1d / 1w") String period,
            @ToolParam(description = "回溯天数，默认 120") int lookbackDays) {
        // 1. 从行情数据库获取历史 OHLCV 数据
        // 2. 按请求的指标列表依次计算
        // 3. 返回指标结果 JSON
        return "...";
    }
}
```

#### 4.3.3 `MarketScreenerTool` — 条件选股（P1）

```java
@Component
public class MarketScreenerTool {

    @Tool(description = """
            根据多维度条件筛选股票。
            
            支持筛选维度：
            - 技术面：均线多头排列、MACD 金叉、RSI 超卖、突破前高 等
            - 基本面：PE 范围、PB 范围、ROE 阈值、营收增速 等
            - 市场面：换手率范围、量比、资金流向 等
            
            返回符合所有条件的股票列表，按综合评分排序。
            """)
    public String market_screener(
            @ToolParam(description = "筛选条件 JSON，如 {'market':'a_share','pe_max':30,'ma_arrangement':'bullish'}") String conditionsJson,
            @ToolParam(description = "返回数量上限，默认 20") int limit) {
        // 1. 解析筛选条件
        // 2. 通过 DatasourceTool 执行多条件 SQL 查询
        // 3. 按自定义评分排序
        return "...";
    }
}
```

#### 4.3.4 `PortfolioAnalyzeTool` — 持仓分析（P1）

```java
@Component
public class PortfolioAnalyzeTool {

    @Tool(description = """
            分析持仓组合的风险收益特征。
            
            分析维度：
            - 仓位结构：行业分布、市值分布、风格分布
            - 风险指标：组合波动率、最大回撤、VaR、Beta
            - 收益分析：累计收益、年化收益、夏普比率
            - 相关性：持仓标的间的相关矩阵
            - 再平衡建议：与目标权重偏差
            
            仅分析，不执行任何交易操作。
            """)
    public String portfolio_analyze(
            @ToolParam(description = "工作区 ID，用于定位用户持仓数据") String workspaceId) {
        // 1. 从工作区读取用户持仓数据
        // 2. 计算各项风险收益指标
        // 3. 与 RulePack 中的风控规则对比
        return "...";
    }
}
```

#### 4.3.5 `TradeJournalTool` — 交易日志（P1）

```java
@Component
public class TradeJournalTool {

    @Tool(description = """
            记录和查询交易日志。
            
            支持操作：
            - record: 记录交易决策（标的、方向、数量、价格、理由、规则依据）
            - query: 查询历史交易记录
            - review: 复盘某笔交易的盈亏和决策质量
            """)
    public String trade_journal(
            @ToolParam(description = "操作类型：record / query / review") String action,
            @ToolParam(description = "操作参数 JSON") String paramsJson) {
        // 1. 写入 mate_trade_journal 表
        // 2. 关联 RulePack 规则引用
        return "...";
    }
}
```

### 4.4 RulePack 系统设计

借鉴 Teacher Agent 的成功经验，交易 Agent 采用四层规则体系：

#### 4.4.1 风控规则包 (`trading.rulepack.risk_management.v1`)

```json
{
  "id": "trading.rulepack.risk_management.v1",
  "name": "交易风控规则",
  "version": "1.0.0",
  "domain": "securities_trading",
  "stage": "all",
  "rules": {
    "position_limits": {
      "max_single_stock_pct": 0.20,
      "max_single_industry_pct": 0.40,
      "max_total_position_pct": 0.80,
      "min_cash_reserve_pct": 0.20
    },
    "stop_loss": {
      "default_stop_loss_pct": -0.08,
      "trailing_stop_enabled": true,
      "trailing_stop_pct": 0.05
    },
    "drawdown_control": {
      "max_daily_drawdown_pct": -0.05,
      "max_weekly_drawdown_pct": -0.10,
      "max_total_drawdown_pct": -0.20
    },
    "trade_frequency": {
      "max_daily_trades": 10,
      "min_holding_days": 1
    },
    "market_cap_filter": {
      "min_market_cap_billion": 5.0,
      "exclude_st_asterisk": true,
      "exclude_new_listing_days": 60
    }
  },
  "acceptance": {
    "required_checks": [
      "position_within_limits",
      "stop_loss_set",
      "not_st_stock",
      "sufficient_liquidity"
    ]
  }
}
```

#### 4.4.2 技术策略规则包 (`trading.rulepack.technical_strategy.v1`)

```json
{
  "id": "trading.rulepack.technical_strategy.v1",
  "name": "技术策略规则",
  "version": "1.0.0",
  "domain": "securities_trading",
  "strategies": {
    "trend_following": {
      "entry": "MA5 > MA20 > MA60 AND price > MA5 AND MACD > 0",
      "exit": "price < MA20 OR MACD < 0",
      "timeframe": "daily"
    },
    "breakout": {
      "entry": "price > highest(high, 20) AND volume > avg(volume, 20) * 1.5",
      "exit": "price < MA10",
      "timeframe": "daily"
    },
    "oversold_bounce": {
      "entry": "RSI(14) < 30 AND price > prev_close",
      "exit": "RSI(14) > 50 OR price < MA5",
      "timeframe": "daily"
    }
  }
}
```

#### 4.4.3 合规规则包 (`trading.rulepack.compliance.v1`)

```json
{
  "id": "trading.rulepack.compliance.v1",
  "name": "交易合规规则",
  "version": "1.0.0",
  "domain": "securities_trading",
  "market_rules": {
    "a_share": {
      "t_plus_1": true,
      "price_limit_pct": 0.10,
      "min_trade_unit": 100,
      "trading_hours": "09:30-11:30,13:00-15:00",
      "auction_hours": "09:15-09:25"
    }
  },
  "prohibited_behaviors": [
    "insider_trading",
    "market_manipulation",
    "wash_trading",
    "front_running"
  ]
}
```

### 4.5 StateGraph 流程设计

交易 Agent 采用增强版 **Plan-to-Trade** StateGraph，核心节点如下：

```
                     START
                       │
                       ▼
              ┌────────────────┐
              │  INTENT_NODE   │  识别用户意图：
              │  (意图识别)     │  market_overview / stock_analysis /
              └───────┬────────┘  portfolio_review / strategy_backtest /
                      │           trade_decision / journal_review
                      ▼
              ┌────────────────┐
              │  CONTEXT_NODE  │  加载上下文：
              │  (上下文加载)   │  行情数据、持仓信息、RulePack、知识库
              └───────┬────────┘
                      │
         ┌────────────┼────────────┐
         ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │ ANALYSIS │ │SCREENING │ │ BACKTEST     │
   │ (分析)   │ │ (选股)    │ │ (回测)       │
   └────┬─────┘ └────┬─────┘ └──────┬───────┘
        │            │              │
        └────────────┼──────────────┘
                     ▼
              ┌────────────────┐
              │  REPORT_NODE   │  生成分析报告：
              │  (报告生成)     │  Markdown + JSON 结构化输出
              └───────┬────────┘
                      │
                是否需要交易？
                ┌────┴────┐
                ▼         ▼
           ┌───────┐  ┌────────┐
           │  NO   │  │  YES   │
           └───┬───┘  └───┬────┘
               │           ▼
               │    ┌──────────────┐
               │    │ TRADE_PLAN   │  生成交易方案：
               │    │ (交易方案)    │  标的/方向/数量/价格/止损/依据
               │    └──────┬───────┘
               │           ▼
               │    ┌──────────────┐
               │    │ RISK_CHECK   │  RulePack 风控校验：
               │    │ (风控校验)    │  仓位上限、止损设置、合规检查
               │    └──────┬───────┘
               │           ▼
               │    ┌──────────────┐
               │    │ APPROVAL     │  触发审批卡：
               │    │ (审批门控)    │  展示方案、风险、替代路径
               │    └──────┬───────┘
               │           ▼
               │    ┌──────────────┐
               │    │ EXECUTION    │  执行交易（审批通过后）：
               │    │ (交易执行)    │  模拟下单 / 记录决策
               │    └──────┬───────┘
               │           │
               └───────────┼───────────┘
                           ▼
                    ┌──────────────┐
                    │ JOURNAL      │  记录交易日志：
                    │ (日志记录)    │  时间/决策/结果/规则依据
                    └──────┬───────┘
                           ▼
                    ┌──────────────┐
                    │ FINAL_ANSWER │  汇总输出：
                    │ (最终回复)    │  分析结论 + 交易摘要 + 风险提示
                    └──────┬───────┘
                           ▼
                          END
```

### 4.6 数据模型设计

```sql
-- 交易日志表
CREATE TABLE mate_trade_journal (
    id BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    agent_id VARCHAR(64),
    conversation_id BIGINT,
    trade_date DATE NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,        -- BUY / SELL
    quantity DECIMAL(16,2),
    price DECIMAL(16,4),
    reason TEXT,                           -- 交易理由
    rule_pack_id VARCHAR(128),             -- 引用的 RulePack
    strategy_id VARCHAR(128),              -- 引用的策略
    result_pnl DECIMAL(16,4),              -- 盈亏
    review_notes TEXT,                     -- 复盘笔记
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 行情数据缓存表（通过 DatasourceTool 连接的外部数据库）
-- 建议使用独立的行情数据库，避免影响主库性能
-- 如使用 Tushare/AKShare 定时同步

-- 自选股/监控列表
CREATE TABLE mate_watchlist (
    id BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    symbol_name VARCHAR(128),
    alert_conditions JSON,                 -- 预警条件
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ws_symbol (workspace_id, symbol)
);
```

### 4.7 Skill 体系设计

```json
{
  "skills": [
    {
      "id": "trading.skill.pre_market_analysis",
      "name": "盘前分析",
      "description": "盘前聚合隔夜外盘、政策消息、个股公告，生成当日交易计划",
      "applicableRulePacks": ["risk_management", "technical_strategy"],
      "steps": ["外盘扫描", "政策解读", "公告筛选", "标的池更新"]
    },
    {
      "id": "trading.skill.intraday_monitor",
      "name": "盘中监控",
      "description": "实时监控自选股的技术指标和异动情况",
      "applicableRulePacks": ["risk_management", "technical_strategy"],
      "steps": ["指标刷新", "异动检测", "预警推送"]
    },
    {
      "id": "trading.skill.post_market_review",
      "name": "盘后复盘",
      "description": "复盘当日交易，分析盈亏原因，沉淀经验",
      "applicableRulePacks": ["risk_management"],
      "steps": ["成交核对", "盈亏归因", "策略评估", "知识沉淀"]
    },
    {
      "id": "trading.skill.strategy_backtest",
      "name": "策略回测",
      "description": "对交易策略进行历史数据回测，输出收益曲线和风险指标",
      "applicableRulePacks": ["technical_strategy"],
      "steps": ["数据准备", "回测执行", "指标计算", "报告生成"]
    }
  ]
}
```

### 4.8 审批流设计

```
交易方案生成
  → 风控校验（自动）：
    ✓ 仓位上限校验
    ✓ 止损设置校验
    ✓ 标的合规校验
    ✓ 交易时间校验
  → 审批卡（人工）：
    ┌─────────────────────────────┐
    │ 🔴 交易审批                 │
    │                             │
    │ 标的：贵州茅台 (600519.SH)   │
    │ 方向：买入                  │
    │ 数量：100 股                │
    │ 价格：限价 1650.00          │
    │ 金额：约 165,000 元         │
    │                             │
    │ 仓位变化：12% → 18%（上限20%）│
    │ 止损位：1518.00（-8%）      │
    │                             │
    │ 风险等级：⚠️ 中              │
    │ 策略依据：趋势跟随策略       │
    │ RulePack：risk_management.v1 │
    │                             │
    │ [批准本次] [批准本会话]     │
    │ [批准本工作区] [拒绝]       │
    └─────────────────────────────┘
```

### 4.9 定时任务设计

```json
{
  "cronJobs": [
    {
      "name": "盘前数据准备",
      "cron": "0 30 8 * * 1-5",
      "action": "拉动隔夜外盘数据 + 当日公告 + 触发盘前分析 Skill",
      "enabled": true
    },
    {
      "name": "盘中监控轮询",
      "cron": "0 */5 9-15 * * 1-5",
      "action": "刷新自选股技术指标 + 检测预警条件",
      "enabled": false
    },
    {
      "name": "盘后数据同步",
      "cron": "0 30 15 * * 1-5",
      "action": "更新日线数据 + 生成本交易日日志快照",
      "enabled": true
    },
    {
      "name": "周度复盘",
      "cron": "0 0 16 * * 5",
      "action": "生成周度交易报告：盈亏汇总、策略胜率、最大回撤",
      "enabled": true
    }
  ]
}
```

---

## 五、分阶段实施计划

### Phase 0：基础设施搭建（1-2 周）

| 任务 | 说明 | 交付物 |
|------|------|--------|
| 行情数据源接入 | 通过 DatasourceTool 连接外部行情数据库（MySQL/PostgreSQL），编写数据同步脚本 | 行情库 schema + 同步脚本 |
| 模板定义 | 编写 `trading-agent.json` 模板，注册到系统内置模板列表 | 模板 JSON 文件 |
| 基础工具 v1 | 实现 `MarketQuoteTool` 和 `MarketIndicatorTool` | 2 个 @Tool Bean |

**验收标准**：Agent 能查询股票行情并计算 MA/MACD 指标。

### Phase 1：分析能力建设（2-3 周）

| 任务 | 说明 | 交付物 |
|------|------|--------|
| 风控 RulePack | 落地 `trading.rulepack.risk_management.v1` | JSON 规则包 |
| `MarketScreenerTool` | 实现条件选股工具 | 1 个 @Tool Bean |
| `PortfolioAnalyzeTool` | 实现持仓分析工具 | 1 个 @Tool Bean |
| `TradeJournalTool` | 实现交易日志工具 | 1 个 @Tool Bean |
| 意图识别 | 扩展 StateGraph 的 IntentNode，识别 6 类交易意图 | IntentService |
| Plan-to-Trade Graph | 构建完整的 ANALYSIS → REPORT 链路 | StateGraph 节点 |

**验收标准**：能完成"分析某只股票 → 筛选潜力标的 → 评估持仓风险 → 记录决策"的完整分析闭环。

### Phase 2：策略与回测（2-3 周）

| 任务 | 说明 | 交付物 |
|------|------|--------|
| 技术策略 RulePack | 落地 `trading.rulepack.technical_strategy.v1` | JSON 规则包 |
| 合规 RulePack | 落地 `trading.rulepack.compliance.v1` | JSON 规则包 |
| `StrategyBacktestTool` | 实现策略回测工具（基于历史行情数据） | 1 个 @Tool Bean |
| 交易审批集成 | 对接现有审批卡系统，实现交易执行审批 | TradeApprovalGuard |
| Skill 体系 | 落地盘前/盘中/盘后/回测四个 Skill | 4 个 Skill 定义 |
| 定时任务 | 配置盘前数据准备和盘后同步的 CronJob | CronJob 配置 |

**验收标准**：能运行"回测趋势策略 → 基于回测结果生成今日计划 → 审批后记录模拟交易"的完整流程。

### Phase 3：优化与生产化（持续）

| 任务 | 说明 |
|------|------|
| Harness 验收 v1 | 规则驱动的交易分析质量验收 |
| Self-Improve 草案 | 从交易日志中生成策略优化建议草案 |
| 多市场支持 | 港股、美股、期货行情接入 |
| 多因子模型 | 基本面 + 技术面 + 情绪面联合打分 |
| 知识库深化 | 行业研报自动入库、法规更新同步 |

---

## 六、风险与注意事项

1. **行情数据源**：建议使用 Tushare Pro（需 token）或 AKShare（免费开源）作为数据源，通过 Python 脚本定时同步到 MySQL
2. **实盘交易**：强烈建议 Phase 3 之前仅支持模拟交易（Paper Trading），实盘接口对接需满足合规要求
3. **系统负载**：盘中实时监控若轮询间隔过短（< 1 分钟），需评估数据库查询压力
4. **回测准确性**：回测结果受数据质量、滑点假设、手续费假设等影响，需在 RulePack 中明确前提假设
5. **法规遵循**：不同市场（A 股/港股/美股）的合规规则有差异，RulePack 需按市场隔离

---

## 七、与现有 Agent 体系的差异对比

| 维度 | Teacher Agent | Trading Agent |
|------|--------------|---------------|
| 核心数据 | 静态知识库（教材/课标/名著） | 动态行情数据库 + 静态知识库 |
| 时间敏感性 | 低（教材版本级更新） | 高（分钟级行情变化） |
| 规则复杂度 | 题型比例/分值规则 | 风控阈值/策略参数/合规规则 |
| 审批需求 | 中（工具调用的低风险审批） | 高（交易执行的 critical 级审批） |
| 定时任务 | 无 | 强依赖（盘前/盘中/盘后） |
| 输出形式 | 试题 + 答案 + 采分点 | 分析报告 + 交易方案 + 损益记录 |
| 自优化方向 | 规则包 fine-tune | 策略参数优化 + 经验沉淀 |
| 用户交互模式 | 命题方案 → 确认 → 生成 | 分析 → 方案 → 审批 → 执行 → 复盘 |

---

## 八、总结

**价值**：交易 Agent 填补了 MateClaw 在金融领域的空白，充分利用了平台已有的数据连接、定时调度、审批流、知识库和 RulePack 基础设施。它在交易决策分析、策略研究、复盘学习等场景有清晰的用户价值。

**可行性**：技术基础设施就绪度约 70%，主要缺口在市场行情数据接入和技术指标计算，均为标准工程问题。建议先以"分析型 Agent"落地，明确不做自动实盘交易，降低合规风险。

**推荐优先级**：在 Teacher Agent v2 稳定后，作为第二个垂直领域 Agent 实施。建议按 Phase 0 → Phase 1 → Phase 2 的顺序渐进式交付，每阶段都有独立的验收标准。
