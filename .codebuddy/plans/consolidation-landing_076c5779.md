---
name: consolidation-landing
overview: Solidify WP-0 to WP-6 analysis and implement WP-1 through WP-6 with zero-breaking changes.
todos:
  - id: wp0-doc-freeze
    content: 固化 WP-0 文档：补充代码锚点、TODO 状态、差异矩阵，冻结基线
    status: completed
  - id: wp1-policy-contract
    content: WP-1 落地：新建 UnifiedPolicyContract、WorkspacePolicyResolver、AgentPolicyResolver，迁移 WorkspaceService / AgentService 解析逻辑
    status: completed
    dependencies:
      - wp0-doc-freeze
  - id: wp2-memory-contract
    content: WP-2 落地：新建 MemoryReadContract / MemoryWriteContract / MemoryResolver，ContextRouterService 记忆逻辑归拢
    status: completed
    dependencies:
      - wp1-policy-contract
  - id: wp4-lifecycle-contract
    content: WP-4 落地：新建 AgentLifecycleContract / LifecycleEventNormalizer，对齐 ReActLifecycleListener 与 MemoryLifecycleMediator 事件术语
    status: completed
    dependencies:
      - wp2-memory-contract
  - id: wp3-context-provider
    content: WP-3 落地：新建 ContextSourceProvider 体系，拆分 ContextRouterService 为 Project/Memory/Wiki/Session Provider
    status: completed
    dependencies:
      - wp4-lifecycle-contract
  - id: wp5-hook-contract
    content: WP-5 落地：新建 HookDispatchContract / ExecutionGuardConfig，统一 HookDispatcher / AsyncTaskService / ToolExecutionExecutor 横切配置
    status: completed
    dependencies:
      - wp3-context-provider
  - id: wp6-template-contract
    content: WP-6 落地：新建 TemplateApplicationContract / TemplateMetadataResolver，对齐 TemplateService 与 SkillTemplate 元数据解析
    status: completed
    dependencies:
      - wp5-hook-contract
---

## 项目概述

将 `docs/core-systems-consolidation-plan.md` 中 WP-0~WP-6 的 first-pass 分析固化为正式工程基线，补充代码验证锚点后，按 WP-1 → WP-2 → WP-4 → WP-3 → WP-5 → WP-6 的顺序进行代码落地。

## 核心需求

- **WP-0 冻结**：在现有 6198 行分析文档基础上，补充精确的代码文件路径锚点、TODO 状态标记、差异矩阵，使其成为可执行的工程基线。
- **WP-1 策略入口统一**：统一 `WorkspacePolicy`（workspace 级）与 `AgentEntity`（agent 级，含 profileId / capabilityPackId / templateMetadataJson）的策略语义，新建统一策略查询合同。
- **WP-2 记忆面统一**：统一 `MemoryLifecycleMediator`、`MemoryManager`、`WorkspaceMemoryTool` 的记忆读写语义，建立记忆查询统一参数合同。
- **WP-3 上下文面统一**：在 WP-1/WP-2 完成后，将 `ContextRouterService` 中混合的 ProjectInsight、记忆摘要、Wiki 路由拆分为独立 `ContextSourceProvider`。
- **WP-4 生命周期统一**：统一 `ReActLifecycleListener`（Agent 图生命周期）、`MemoryLifecycleMediator`（记忆生命周期）、`CronJobLifecycleService`（定时任务生命周期）的事件术语体系。
- **WP-5 钩子面统一**：统一 `HookDispatcher`（事件驱动钩子）、`AsyncTaskService`（异步任务轮询）、`ToolExecutionExecutor`（工具执行链）的语义边界与横切配置。
- **WP-6 模板契约统一**：统一 `TemplateService`（Agent 模板）与 `SkillTemplate`（Skill 模板）的应用流程与元数据解析语义。

## 约束条件

- 每个 WP 的第一批改动只增加新的接口 / 解析器 / facade，不删除或修改现有字段 / 表结构。
- 优先提取 / 包装现有行为，而非重写。
- 每个 WP 完成后运行现有测试套件确保无回归。
- 新增代码放在独立的 `*.contract.*` / `*.resolver.*` 包中，不与现有业务代码混合。

## 技术栈

- **后端框架**：Java 21 + Spring Boot + MyBatis-Plus
- **AI 框架**：Spring AI Alibaba (spring-ai-alibaba-graph-core)
- **缓存**：Caffeine
- **构建工具**：Maven
- **测试**：JUnit 5 + 虚拟线程并发测试

## 实施方案

### 总体策略

采用"兼容性批次"策略：每个 WP 先建立只读合同接口（contract）和集中解析器（resolver），现有业务逻辑逐步迁移调用方，最后一批次才考虑废弃旧路径。这样保证每一阶段都可编译、可测试、可回滚。

### 关键技术决策

1. **合同接口优先**：每个 WP 新建 `*Contract` 接口定义统一查询语义，现有 Service 实现该接口作为默认实现，避免 breaking change。
2. **解析器集中化**：将散落在 Service / Entity / DTO 中的 JSON 解析逻辑（如 `settingsJson`、`templateMetadataJson`）集中到 `*Resolver` 组件，统一处理版本兼容和字段映射。
3. **包命名空间隔离**：每个 WP 的新增代码使用 `vip.mate.{domain}.contract` 和 `vip.mate.{domain}.resolver` 包，与现有业务包物理隔离。
4. **事件体系对齐**：WP-4 使用 Spring `ApplicationEvent` 作为统一事件总线，`MemoryLifecycleMediator` 已使用此模式，`ReActLifecycleListener` 从 `GraphLifecycleListener` 对齐到同一事件体系。

### 性能考量

- `ContextRouterService.buildInjectionBlock` 有 2400 字符预算硬约束，重构时需保持预算控制逻辑不变。
- `MemoryLifecycleMediator.beforeLlmCall` 是同步热路径，合同接口需保持零额外开销（默认方法或内联调用）。
- `HookDispatcher` 使用虚拟线程和 Semaphore 保护，WP-5 的横切配置统一不得引入额外的线程同步开销。

### 回归控制

- 每个 WP 修改后执行 `mvn test` 验证。
- 优先复用现有 `MemoryLifecycleMediatorTest`、`HookDispatcherTest`、`RepetitionDetectorTest` 等测试基线。

## 架构设计

```
mateclaw-server/src/main/java/vip/mate/
├── workspace/
│   ├── core/model/WorkspacePolicy.java          [MODIFY] 补充统一合同实现
│   ├── core/service/WorkspaceService.java       [MODIFY] 解析逻辑迁移到 Resolver
│   └── policy/                                  [NEW] WP-1 合同与解析器
│       ├── UnifiedPolicyContract.java
│       ├── WorkspacePolicyResolver.java
│       └── AgentPolicyResolver.java
├── agent/
│   ├── model/AgentEntity.java                   [MODIFY] 无字段变更，仅补充注解
│   ├── AgentService.java                        [MODIFY] 接入统一策略合同
│   ├── context/ContextRouterService.java        [MODIFY] WP-3 拆分为 Provider 调用
│   └── service/TemplateService.java             [MODIFY] WP-6 接入模板元数据解析器
├── memory/
│   ├── lifecycle/MemoryLifecycleMediator.java   [MODIFY] 接入记忆合同
│   ├── spi/MemoryManager.java                   [MODIFY] 无签名变更
│   └── contract/                                [NEW] WP-2 记忆合同
│       ├── MemoryReadContract.java
│       ├── MemoryWriteContract.java
│       └── MemoryResolver.java
├── context/
│   └── provider/                                [NEW] WP-3 上下文源提供者
│       ├── ContextSourceProvider.java
│       ├── ProjectContextProvider.java
│       ├── MemoryContextProvider.java
│       └── WikiContextProvider.java
├── lifecycle/
│   └── contract/                                [NEW] WP-4 生命周期合同
│       ├── AgentLifecycleContract.java
│       └── LifecycleEventNormalizer.java
├── hook/
│   ├── HookDispatcher.java                      [MODIFY] 接入统一配置
│   ├── contract/                                [NEW] WP-5 钩子合同
│   │   ├── HookDispatchContract.java
│   │   └── ExecutionGuardConfig.java
│   └── resolver/                                [NEW]
│       └── GuardConfigResolver.java
├── template/
│   └── contract/                                [NEW] WP-6 模板合同
│       ├── TemplateApplicationContract.java
│       └── TemplateMetadataResolver.java
└── planning/
    └── service/PlanningService.java             [MODIFY] 状态术语对齐
```

## 关键代码结构

```java
// WP-1 统一策略查询合同
public interface UnifiedPolicyContract {
    SandboxMode sandboxMode(Long workspaceId, Long agentId);
    ApprovalPolicy approvalPolicy(Long workspaceId, Long agentId);
    NetworkPolicy networkPolicy(Long workspaceId, Long agentId);
    List<String> allowedPaths(Long workspaceId, Long agentId);
    List<String> deniedPaths(Long workspaceId, Long agentId);
}

// WP-2 记忆读写合同
public interface MemoryReadContract {
    String prefetch(Long agentId, String query, MemoryBudget budget);
}

// WP-4 生命周期事件标准化
public interface AgentLifecycleContract {
    void onTurnStart(TurnContext ctx);
    void onTurnComplete(TurnContext ctx, String assistantReply);
    void onSessionEnd(Long agentId, String conversationId);
}

// WP-6 模板元数据解析器
public class TemplateMetadataResolver {
    public TemplateRouteConfig resolveRouteConfig(String templateMetadataJson);
    public AgentProfileSnapshot resolveProfile(String templateMetadataJson);
}
```