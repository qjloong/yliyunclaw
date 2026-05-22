---
name: 完善 Ollama 支持与 Token 统计
overview: 当禁用其他模型、只保留 Ollama（deepseek-r1 等不支持 tools 的模型）时，Agent 因始终附加 tool callbacks 导致 400 报错。计划提取模型能力检查并在构建 Agent 时根据模型能力禁用 tools，同时补充 token 统计与错误提示。
todos:
  - id: extract-model-capability
    content: 提取 Ollama 模型能力判断到公共 ModelCapability 工具类
    status: completed
  - id: agent-toolset-filter
    content: 在 AgentGraphBuilder 中根据模型 tool 支持能力过滤 toolSet
    status: completed
    dependencies:
      - extract-model-capability
  - id: enhance-error-hint
    content: 增强 NodeStreamingChatHelper 无 fallback 时的错误提示与 cooldown 诊断
    status: completed
  - id: fix-token-stats
    content: 修正 TokenUsageService 统计范围，保留 stopped/interrupted 状态的 token 记录
    status: completed
  - id: verify-empty-toolset
    content: Use [subagent:code-explorer] 验证空 toolSet 在 PlanGenerationNode 和 StepExecutionNode 中的兼容性
    status: completed
    dependencies:
      - agent-toolset-filter
---

## 问题概述

当禁用其他模型、只保留 Ollama（deepseek-r1:latest）时，发起会话调用报错：

- `Primary provider=ollama in cooldown — skipping straight to fallback chain`
- `LLM 调用失败，已达最大重试次数`

## 根因分析

1. **模型能力不匹配**：`deepseek-r1` 属于 `OllamaAutoDiscoveryRunner.KNOWN_NO_TOOL_FAMILIES`，不支持 function calling / tools
2. **Agent 构建时未检测能力**：`AgentGraphBuilder` 始终向 Plan-Execute / ReAct Agent 传入完整 `toolSet`
3. **StepExecutionNode 强制附加 toolCallbacks**：导致 Ollama 返回 400 `"does not support tools"`
4. **错误进入 cooldown 循环**：`NodeStreamingChatHelper` 将 400 分类为 `CLIENT_ERROR` 不重试；`ProviderHealthTracker` 记录连续失败后进入 cooldown
5. **无 fallback 直接失败**：禁用其他模型后 fallback chain 为空，跳过 primary 后无模型可调用

## 核心需求

1. **完善 Ollama 支持**：构建 Agent 时根据模型能力自动禁用 tools，使不支持 tools 的 Ollama 模型可正常进行纯文本问答
2. **增强错误提示**：当 primary 处于 cooldown 且无 fallback 时，给出明确原因（模型不支持工具 / 无备选模型）
3. **完善 Token 统计**：修正 `TokenUsageService` 将 `stopped`/`interrupted` 状态（可能已有估算 token）误排除的问题；确保 Ollama 流式调用的 token 估算被持久化

## 技术栈

- Java 17 / Spring Boot
- Spring AI（OpenAI-compatible 协议对接 Ollama）
- spring-ai-alibaba-graph-core（StateGraph 引擎）
- MyBatis-Plus（TokenUsageService 查询）

## 实现方案

### 1. 模型能力检测与工具降级

- 将 `OllamaAutoDiscoveryRunner` 中的 `KNOWN_NO_TOOL_FAMILIES` 和 `supportsTools` 逻辑提取为公共类 `ModelCapability`
- 在 `AgentGraphBuilder.buildAgent` 中，调用 `ModelCapability.supportsTools(runtimeModel)` 判断当前运行时模型是否支持工具调用
- 若不支持，将 `toolSet` 替换为空的 `AgentToolSet`（`AgentToolSet.fromCallbacks(List.of(), List.of())`）
- 空 toolSet 使得 `PlanGenerationNode` 看不到可用工具，对简单问题自动走 `direct_answer` 路径；`StepExecutionNode` 的 `OpenAiChatOptions` 不附加 `toolCallbacks`，避免 Ollama 400 错误

### 2. 无 Fallback 错误提示增强

- 修改 `NodeStreamingChatHelper.streamCallInternal`：当 `primarySkipped=true` 且 `fallbackChain.isEmpty()` 时，返回更具诊断价值的错误信息，明确提示"主模型不可用且无备选模型"或"当前模型不支持工具调用"
- 保持现有 `ProviderHealthTracker` cooldown 机制不变，仅在最终错误消息上增强可读性

### 3. Token 统计修复

- `TokenUsageService` 当前已修改版本排除了 `stopped`、`interrupted`、`error` 三种状态
- 但 `NodeStreamingChatHelper` 已在用户 stop / stream 中断路径中调用 `estimateTokensIfMissing.run()`，因此 `stopped`/`interrupted` 消息可能携带有效估算 token
- 将查询条件调整为仅排除 `error` 状态，保留 `stopped` 和 `interrupted`，避免 token 统计遗漏

## 架构设计

```
AgentGraphBuilder.buildAgent()
    ├─ ModelCapability.supportsTools(runtimeModel)
    │   └─ 对 ollama provider：按 KNOWN_NO_TOOL_FAMILIES 判断
    ├─ 不支持 tools → toolSet = emptyToolSet
    ├─ 构建 Plan-Execute / ReAct Graph（toolSet 为空时不传 toolCallbacks）
    └─ 正常文本问答
```

## 目录结构

```
mateclaw-server/src/main/java/vip/mate/llm/model/
├── ModelCapability.java                          # [NEW] 模型能力判断工具类（提取 Ollama tool 支持检测）
mateclaw-server/src/main/java/vip/mate/llm/config/
├── OllamaAutoDiscoveryRunner.java                # [MODIFY] 复用 ModelCapability，移除本地私有 supportsTools
mateclaw-server/src/main/java/vip/mate/agent/
├── AgentGraphBuilder.java                        # [MODIFY] 根据模型能力过滤 toolSet，添加降级日志
mateclaw-server/src/main/java/vip/mate/agent/graph/
├── NodeStreamingChatHelper.java                  # [MODIFY] 增强无 fallback 时的错误提示（基于用户已改版本）
mateclaw-server/src/main/java/vip/mate/workspace/conversation/
├── TokenUsageService.java                        # [MODIFY] 放宽统计条件，仅排除 error（基于用户已改版本）
```

## 关键代码结构

```java
// ModelCapability.java
public final class ModelCapability {
    private static final Set<String> KNOWN_NO_TOOL_FAMILIES = Set.of(
        "deepseek-r1", "gemma", "gemma2", "gemma3", "phi3", "phi4",
        "codellama", "llama3.2", "qwen2", "mistral"
    );
    public static boolean supportsTools(ModelConfigEntity model) {
        if (model == null || !"ollama".equals(model.getProvider())) return true;
        String tag = model.getModelName();
        String base = (tag != null && tag.contains(":")) ? tag.substring(0, tag.indexOf(":")) : tag;
        return !KNOWN_NO_TOOL_FAMILIES.contains(base);
    }
}
```

```java
// AgentGraphBuilder.buildAgent 片段
if (!ModelCapability.supportsTools(runtimeModel)) {
    log.warn("Model {} does not support tool calling, disabling tools for agent {}", runtimeModel.getModelName(), entity.getName());
    toolSet = AgentToolSet.fromCallbacks(List.of(), List.of());
}
```

## Agent Extensions

### SubAgent

- **code-explorer**
- Purpose: 验证 `AgentGraphBuilder` 中 `toolSet` 的传递链路，以及 `StepExecutionNode`、`PlanGenerationNode` 中对 `toolSet` 的实际使用位置，确保空 toolSet 不会引发 NPE
- Expected outcome: 确认所有 toolSet 调用点（`toolSet.callbacks()`、`toolSet.isEmpty()` 等）都能正确处理空工具集