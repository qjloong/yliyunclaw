package vip.mate.agent.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import vip.mate.agent.AgentService;
import vip.mate.agent.AgentState;
import vip.mate.agent.BaseAgent;
import vip.mate.agent.GraphEventPublisher;
import vip.mate.agent.StructuredStreamCapable;
import vip.mate.agent.context.ConversationWindowManager;
import vip.mate.agent.graph.state.MateClawStateKeys;
import vip.mate.harness.model.HarnessExecutionSummary;
import vip.mate.harness.service.HarnessRunService;
import vip.mate.tool.image.vision.ImageVisionService;
import vip.mate.workspace.conversation.ConversationService;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static vip.mate.agent.graph.state.MateClawStateKeys.*;

/**
 * 基于 StateGraph v2 的 ReAct Agent
 * <p>
 * 使用 spring-ai-alibaba-graph-core 的 StateGraph 引擎，
 * 实现显式可控的 Thought → Action → Observation 循环，
 * 含 Summarizing、LimitExceeded 和 FinalAnswer 节点。
 * <p>
 * 关键特性：
 * - 迭代次数强制控制（maxIterations 真正生效）
 * - ToolGuard 安全拦截（在 ActionNode 中执行）
 * - 工具调用过程可观测
 * - Summarizing 阶段收束冗长上下文
 * - 超限友好提示
 * - 结构化生命周期日志
 * <p>
 * content_delta 和 thinking_delta 由节点内 {@link NodeStreamingChatHelper} 直推，
 * chatStructuredStream() 只处理 phase/tool/事件等结构化事件。
 * 不再从 NodeOutput 二次整段下发已流式推送的内容。
 *
 * @author MateClaw Team
 */
@Slf4j
public class StateGraphReActAgent extends BaseAgent implements StructuredStreamCapable {

    private final CompiledGraph compiledGraph;
    private final org.springframework.ai.chat.model.ChatModel chatModel;
    private final ConversationWindowManager conversationWindowManager;
    private final HarnessRunService harnessRunService;
    private final List<ToolCallback> toolCallbacks;

    public StateGraphReActAgent(ChatClient chatClient, ConversationService conversationService,
                                CompiledGraph compiledGraph,
                                org.springframework.ai.chat.model.ChatModel chatModel,
                                ConversationWindowManager conversationWindowManager,
                                ImageVisionService imageVisionService,
                                HarnessRunService harnessRunService) {
        this(chatClient, conversationService, compiledGraph, chatModel,
                conversationWindowManager, imageVisionService, harnessRunService, List.of());
    }

    public StateGraphReActAgent(ChatClient chatClient, ConversationService conversationService,
                                CompiledGraph compiledGraph,
                                org.springframework.ai.chat.model.ChatModel chatModel,
                                ConversationWindowManager conversationWindowManager,
                                ImageVisionService imageVisionService,
                                HarnessRunService harnessRunService,
                                List<ToolCallback> toolCallbacks) {
        super(chatClient, conversationService, imageVisionService);
        this.compiledGraph = compiledGraph;
        this.chatModel = chatModel;
        this.conversationWindowManager = conversationWindowManager;
        this.harnessRunService = harnessRunService;
        this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : List.of();
    }

    @Override
    public String chat(String userMessage, String conversationId) {
        setState(AgentState.RUNNING);
        String harnessRunId = startHarnessRun("chat", conversationId);
        try {
            log.info("[{}] StateGraph chat: conversationId={}", agentName, conversationId);

            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if (result.isPresent()) {
                completeHarnessRun(harnessRunId, result.get());
            } else {
                HarnessExecutionSummary summary = new HarnessExecutionSummary();
                summary.setFinishReason("completed");
                harnessRunService.completeRun(harnessRunId, summary);
            }

            return result
                    .flatMap(s -> s.<String>value(FINAL_ANSWER))
                    .orElse("未能生成回答。");
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            log.error("[{}] StateGraph chat failed: {}", agentName, e.getMessage(), e);
            setState(AgentState.ERROR);
            throw new RuntimeException("对话失败：" + e.getMessage(), e);
        } finally {
            if (getState() != AgentState.ERROR) {
                setState(AgentState.IDLE);
            }
        }
    }

    @Override
    public Flux<String> chatStream(String userMessage, String conversationId) {
        setState(AgentState.RUNNING);
        String harnessRunId = startHarnessRun("chat_stream", conversationId);
        try {
            log.info("[{}] StateGraph stream: conversationId={}", agentName, conversationId);

            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            String threadId = UUID.randomUUID().toString();
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            Flux<NodeOutput> observedStream = observeHarnessStream(compiledGraph.stream(inputs, config), harnessRunId);

            return observedStream
                    .filter(this::hasFinalAnswer)
                    .map(this::extractFinalAnswer)
                    .filter(content -> content != null && !content.isEmpty())
                    .next()     // 只取第一个 finalAnswer，避免多个节点重复 emit
                    .flux()
                    .doOnComplete(() -> setState(AgentState.IDLE))
                    .doOnError(e -> {
                        log.error("[{}] StateGraph stream error: {}", agentName, e.getMessage());
                        setState(AgentState.ERROR);
                    });
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            log.error("[{}] StateGraph stream setup failed: {}", agentName, e.getMessage(), e);
            setState(AgentState.ERROR);
            return Flux.error(e);
        }
    }

    @Override
    public String execute(String goal, String conversationId) {
        return chat(goal, conversationId);
    }

    @Override
    public String chatWithReplay(String userMessage, String conversationId, String toolCallPayload) {
        setState(AgentState.RUNNING);
        String harnessRunId = startHarnessRun("chat_replay", conversationId);
        try {
            log.info("[{}] StateGraph chatWithReplay: conversationId={}", agentName, conversationId);

            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            if (toolCallPayload != null && !toolCallPayload.isEmpty()) {
                inputs.put(FORCED_TOOL_CALL, toolCallPayload);
            }
            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if (result.isPresent()) {
                completeHarnessRun(harnessRunId, result.get());
            } else {
                HarnessExecutionSummary summary = new HarnessExecutionSummary();
                summary.setFinishReason("completed");
                harnessRunService.completeRun(harnessRunId, summary);
            }

            return result
                    .flatMap(s -> s.<String>value(FINAL_ANSWER))
                    .orElse("工具已执行。");
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            log.error("[{}] StateGraph chatWithReplay failed: {}", agentName, e.getMessage(), e);
            setState(AgentState.ERROR);
            throw new RuntimeException("重放执行失败：" + e.getMessage(), e);
        } finally {
            if (getState() != AgentState.ERROR) {
                setState(AgentState.IDLE);
            }
        }
    }

    @Override
    public Flux<AgentService.StreamDelta> chatWithReplayStream(String userMessage, String conversationId,
                                                                String toolCallPayload) {
        return chatWithReplayStream(userMessage, conversationId, toolCallPayload, "");
    }

    @Override
    public Flux<AgentService.StreamDelta> chatWithReplayStream(String userMessage, String conversationId,
                                                                String toolCallPayload, String requesterId) {
        setState(AgentState.RUNNING);
        String harnessRunId = startHarnessRun("chat_replay_stream", conversationId);
        try {
            log.info("[{}] StateGraph chatWithReplayStream: conversationId={}", agentName, conversationId);

            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            inputs.put(REQUESTER_ID, requesterId != null ? requesterId : "");
            if (toolCallPayload != null && !toolCallPayload.isEmpty()) {
                inputs.put(FORCED_TOOL_CALL, toolCallPayload);
            }
            String threadId = UUID.randomUUID().toString();
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            Flux<NodeOutput> observedStream = observeHarnessStream(compiledGraph.stream(inputs, config), harnessRunId);

            AtomicInteger sentEventCount = new AtomicInteger(0);
            AtomicInteger finalPromptTokens = new AtomicInteger(0);
            AtomicInteger finalCompletionTokens = new AtomicInteger(0);
            AtomicReference<String> finalModelName = new AtomicReference<>("");
            AtomicReference<String> finalProviderId = new AtomicReference<>("");
            // 防重保护：同 chatStructuredStream
            AtomicBoolean finalAnswerEmitted = new AtomicBoolean(false);
            AtomicBoolean finalThinkingEmitted = new AtomicBoolean(false);
            AtomicReference<String> lastEmittedStreamedContent = new AtomicReference<>("");

            return observedStream
                    .flatMapIterable(output -> {
                        List<AgentService.StreamDelta> deltas = new ArrayList<>();
                        List<GraphEventPublisher.GraphEvent> allEvents = GraphEventPublisher.extractEvents(output);
                        int newStart = sentEventCount.get();
                        if (newStart < allEvents.size()) {
                            for (int i = newStart; i < allEvents.size(); i++) {
                                var event = allEvents.get(i);
                                deltas.add(AgentService.StreamDelta.event(event.type(), event.data()));
                            }
                            sentEventCount.set(allEvents.size());
                        }

                        boolean contentAlreadyStreamed = output.state().value(CONTENT_STREAMED, false);
                        boolean thinkingAlreadyStreamed = output.state().value(THINKING_STREAMED, false);

                        // 与 chatStructuredStream 一致：把每轮 STREAMED_CONTENT 用 persistOnly 推给 Accumulator，
                        // 否则中间叙述（reasoning narrative + summarize）只在 SSE 上出现一次，刷新后丢失。
                        String streamed = output.state().<String>value(STREAMED_CONTENT).orElse("");
                        if (!streamed.isEmpty() && !streamed.equals(lastEmittedStreamedContent.get())) {
                            lastEmittedStreamedContent.set(streamed);
                            deltas.add(AgentService.StreamDelta.persistOnly(streamed, null));
                        }

                        if (hasFinalAnswer(output) && finalAnswerEmitted.compareAndSet(false, true)) {
                            String answer = extractFinalAnswer(output);
                            if (answer != null && !answer.isEmpty()) {
                                deltas.add(contentAlreadyStreamed
                                        ? AgentService.StreamDelta.persistOnly(answer, null)
                                        : new AgentService.StreamDelta(answer, null));
                            }
                        }
                        String thinking = extractFinalThinking(output);
                        if (thinking != null && !thinking.isEmpty()
                                && finalThinkingEmitted.compareAndSet(false, true)) {
                            deltas.add(thinkingAlreadyStreamed
                                    ? AgentService.StreamDelta.persistOnly(null, thinking)
                                    : new AgentService.StreamDelta(null, thinking));
                        }

                        finalPromptTokens.set(output.state().value(PROMPT_TOKENS, 0));
                        finalCompletionTokens.set(output.state().value(COMPLETION_TOKENS, 0));
                        finalModelName.set(output.state().value(RUNTIME_MODEL_NAME, ""));
                        finalProviderId.set(output.state().value(RUNTIME_PROVIDER_ID, ""));

                        return deltas;
                    })
                    .concatWith(Mono.fromSupplier(() -> {
                        String modelName = finalModelName.get();
                        String providerId = finalProviderId.get();
                        Map<String, Object> usageData = new HashMap<>();
                        usageData.put("promptTokens", finalPromptTokens.get());
                        usageData.put("completionTokens", finalCompletionTokens.get());
                        usageData.put("runtimeModelName", modelName != null ? modelName : "");
                        usageData.put("runtimeProviderId", providerId != null ? providerId : "");
                        return AgentService.StreamDelta.event("_usage_final", usageData);
                    }).flatMapMany(Flux::just))
                    .doOnComplete(() -> setState(AgentState.IDLE))
                    .doOnError(e -> {
                        log.error("[{}] StateGraph replay stream error: {}", agentName, e.getMessage());
                        setState(AgentState.ERROR);
                    });
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            setState(AgentState.ERROR);
            return Flux.error(e);
        }
    }

    @Override
    public Flux<AgentService.StreamDelta> chatStructuredStream(String userMessage, String conversationId) {
        return chatStructuredStream(userMessage, conversationId, "");
    }

    @Override
    public Flux<AgentService.StreamDelta> chatStructuredStream(String userMessage, String conversationId,
                                                                String requesterId) {
        setState(AgentState.RUNNING);
        String harnessRunId = startHarnessRun("chat_structured_stream", conversationId);
        try {
            log.info("[{}] StateGraph structured stream: conversationId={}", agentName, conversationId);

            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            inputs.put(REQUESTER_ID, requesterId != null ? requesterId : "");
            String threadId = UUID.randomUUID().toString();
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            Flux<NodeOutput> observedStream = observeHarnessStream(compiledGraph.stream(inputs, config), harnessRunId);

            // Lambda 内需要维护已发送事件偏移；这里只是局部可变计数器，不涉及跨会话共享。
            AtomicInteger sentEventCount = new AtomicInteger(0);
            // Token usage 追踪（每次 NodeOutput 更新最新累计值，最后一次即最终值）
            AtomicInteger finalPromptTokens = new AtomicInteger(0);
            AtomicInteger finalCompletionTokens = new AtomicInteger(0);
            AtomicReference<String> finalModelName = new AtomicReference<>("");
            AtomicReference<String> finalProviderId = new AtomicReference<>("");
            // 防重保护：StateGraph 对每个节点都 emit NodeOutput，FINAL_ANSWER 一旦写入后续节点都携带，
            // 用 compareAndSet 保证只取第一次，避免 content/thinking 被重复追加
            AtomicBoolean finalAnswerEmitted = new AtomicBoolean(false);
            AtomicBoolean finalThinkingEmitted = new AtomicBoolean(false);
            // STREAMED_CONTENT 是 REPLACE 策略（每轮 ReasoningNode/SummarizingNode 覆写），
            // 用 lastEmitted 跟踪已发送的值，避免在 ActionNode/ObservationNode 的 NodeOutput 上重复发送同一段内容。
            AtomicReference<String> lastEmittedStreamedContent = new AtomicReference<>("");

            return observedStream
                    .flatMapIterable(output -> {
                        List<AgentService.StreamDelta> deltas = new ArrayList<>();
                        // 1. 提取所有累积的事件，只发送新增部分
                        List<GraphEventPublisher.GraphEvent> allEvents = GraphEventPublisher.extractEvents(output);
                        int newStart = sentEventCount.get();
                        if (newStart < allEvents.size()) {
                            for (int i = newStart; i < allEvents.size(); i++) {
                                var event = allEvents.get(i);
                                deltas.add(AgentService.StreamDelta.event(event.type(), event.data()));
                            }
                            sentEventCount.set(allEvents.size());
                        }

                        // 2. 内容始终通过 StreamDelta 发给 Accumulator 用于持久化
                        //    已由 NodeStreamingChatHelper 广播过的标记 persistOnly，避免前端收到重复 content_delta
                        boolean contentAlreadyStreamed = output.state()
                                .value(CONTENT_STREAMED, false);
                        boolean thinkingAlreadyStreamed = output.state()
                                .value(THINKING_STREAMED, false);

                        // 2a. 中间叙述内容持久化：每轮 ReasoningNode（带 tool_calls）和 SummarizingNode
                        //     都把当轮 LLM 输出写入 STREAMED_CONTENT。NodeStreamingChatHelper 已实时广播
                        //     给前端，但 Accumulator 不在 SSE 订阅链路上，必须用 persistOnly StreamDelta
                        //     补一刀，否则刷新后正文文字全部丢失（只剩 final_answer + tool_call 卡片）。
                        String streamed = output.state().<String>value(STREAMED_CONTENT).orElse("");
                        if (!streamed.isEmpty() && !streamed.equals(lastEmittedStreamedContent.get())) {
                            lastEmittedStreamedContent.set(streamed);
                            deltas.add(AgentService.StreamDelta.persistOnly(streamed, null));
                        }

                        if (hasFinalAnswer(output) && finalAnswerEmitted.compareAndSet(false, true)) {
                            String answer = extractFinalAnswer(output);
                            if (answer != null && !answer.isEmpty()) {
                                deltas.add(contentAlreadyStreamed
                                        ? AgentService.StreamDelta.persistOnly(answer, null)
                                        : new AgentService.StreamDelta(answer, null));
                            }
                        }

                        String thinking = extractFinalThinking(output);
                        if (thinking != null && !thinking.isEmpty()
                                && finalThinkingEmitted.compareAndSet(false, true)) {
                            deltas.add(thinkingAlreadyStreamed
                                    ? AgentService.StreamDelta.persistOnly(null, thinking)
                                    : new AgentService.StreamDelta(null, thinking));
                        }

                        // 3. 更新最新累计 token usage
                        finalPromptTokens.set(output.state().value(PROMPT_TOKENS, 0));
                        finalCompletionTokens.set(output.state().value(COMPLETION_TOKENS, 0));
                        finalModelName.set(output.state().value(RUNTIME_MODEL_NAME, ""));
                        finalProviderId.set(output.state().value(RUNTIME_PROVIDER_ID, ""));

                        return deltas;
                    })
                    // 流正常完成后追加内部 usage 事件
                    .concatWith(Mono.fromSupplier(() -> {
                        String modelName = finalModelName.get();
                        String providerId = finalProviderId.get();
                        Map<String, Object> usageData = new HashMap<>();
                        usageData.put("promptTokens", finalPromptTokens.get());
                        usageData.put("completionTokens", finalCompletionTokens.get());
                        usageData.put("runtimeModelName", modelName != null ? modelName : "");
                        usageData.put("runtimeProviderId", providerId != null ? providerId : "");
                        return AgentService.StreamDelta.event("_usage_final", usageData);
                    }).flatMapMany(Flux::just))
                    .doOnComplete(() -> setState(AgentState.IDLE))
                    .doOnError(e -> {
                        log.error("[{}] StateGraph structured stream error: {}", agentName, e.getMessage());
                        setState(AgentState.ERROR);
                    });
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            setState(AgentState.ERROR);
            return Flux.error(e);
        }
    }

    private Map<String, Object> buildInitialState(String userMessage, String conversationId) {
        // 加载会话历史
        List<Message> historyMessages = buildConversationHistory(conversationId, userMessage);

        // 上下文窗口管理：裁剪超出模型 context window 的历史（含当前消息预算）
        if (conversationWindowManager != null) {
            Long parsedAgentId = null;
            try { parsedAgentId = Long.valueOf(agentId); } catch (Exception ignored) {}
            vip.mate.agent.context.ChatOrigin origin = vip.mate.agent.context.ChatOriginHolder.get();
            String effectiveWorkspaceBasePath = origin.workspaceBasePath() != null && !origin.workspaceBasePath().isBlank()
                ? origin.workspaceBasePath()
                : workspaceBasePath;
            historyMessages = conversationWindowManager.fitToWindow(
                    historyMessages,
                    systemPrompt != null ? systemPrompt : "",
                    userMessage,
                    maxInputTokens,
                    chatModel,
                    conversationId,
                parsedAgentId,
                toolCallbacks,
                effectiveWorkspaceBasePath);
        }

        List<Message> messages = new ArrayList<>(historyMessages);
        // 构建当前用户消息：支持 multimodal（如果有图片附件，直接注入 Media）
        messages.add(buildCurrentUserMessage(conversationId, userMessage));

        Map<String, Object> inputs = new HashMap<>();
        // 输入
        inputs.put(USER_MESSAGE, userMessage);
        inputs.put(CONVERSATION_ID, conversationId);
        inputs.put(AGENT_ID, agentId != null ? agentId : "");
        vip.mate.agent.context.ChatOrigin origin = vip.mate.agent.context.ChatOriginHolder.get();
        String effectiveWorkspaceBasePath = origin.workspaceBasePath() != null && !origin.workspaceBasePath().isBlank()
            ? origin.workspaceBasePath()
            : workspaceBasePath;
        inputs.put(WORKSPACE_BASE_PATH, effectiveWorkspaceBasePath != null ? effectiveWorkspaceBasePath : "");
        inputs.put(SYSTEM_PROMPT, systemPrompt != null ? systemPrompt : "你是一个有帮助的AI助手。");
        inputs.put(MESSAGES, messages);
        // 迭代控制：深度思考模式允许更多迭代（思考需要更多轮工具调用）
        String thinkingLevel = vip.mate.agent.ThinkingLevelHolder.get();
        boolean thinkingOn = thinkingLevel != null && !"off".equalsIgnoreCase(thinkingLevel);
        int effectiveMaxIterations = thinkingOn ? maxIterations + 5 : maxIterations;
        inputs.put(MAX_ITERATIONS, effectiveMaxIterations);
        inputs.put(CURRENT_ITERATION, 0);
        // 初始化新字段
        inputs.put(TOOL_CALL_COUNT, 0);
        inputs.put(ERROR_COUNT, 0);
        inputs.put(SHOULD_SUMMARIZE, false);
        inputs.put(LIMIT_EXCEEDED, false);
        inputs.put(CONTENT_STREAMED, false);
        inputs.put(THINKING_STREAMED, false);
        inputs.put(AWAITING_APPROVAL, false);
        inputs.put(STREAMED_CONTENT, "");
        inputs.put(STREAMED_THINKING, "");
        inputs.put(REQUESTER_ID, "");
        inputs.put(FORCED_TOOL_CALL, "");
        inputs.put(PROMPT_TOKENS, 0);
        inputs.put(COMPLETION_TOKENS, 0);
        inputs.put(RUNTIME_MODEL_NAME, modelName != null ? modelName : "");
        inputs.put(RUNTIME_PROVIDER_ID, runtimeProviderId != null ? runtimeProviderId : "");
        inputs.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));

        // RFC-063r §2.5: enrich the originating ChatOrigin with this agent's id
        // and workspace, then write it into graph state so ActionNode +
        // StepExecutionNode can forward it to ToolExecutionExecutor → ToolContext.
        Long parsedAgentIdForOrigin = null;
        try { parsedAgentIdForOrigin = agentId != null ? Long.valueOf(agentId) : null; } catch (Exception ignored) {}
        if (parsedAgentIdForOrigin != null) {
            origin = origin.withAgent(parsedAgentIdForOrigin);
        }
        origin = origin.withConversationId(conversationId)
                .withWorkspace(origin.workspaceId(), effectiveWorkspaceBasePath);
        inputs.put(CHAT_ORIGIN, origin);
        return inputs;
    }

    private boolean hasFinalAnswer(NodeOutput output) {
        if (output == null || output.state() == null) {
            return false;
        }
        return output.state().<String>value(FINAL_ANSWER)
                .filter(s -> !s.isEmpty())
                .isPresent();
    }

    private String extractFinalAnswer(NodeOutput output) {
        return output.state().<String>value(FINAL_ANSWER).orElse("");
    }

    private String extractFinalThinking(NodeOutput output) {
        if (output == null || output.state() == null) {
            return null;
        }
        return output.state().<String>value(FINAL_THINKING).orElse(null);
    }

    private String startHarnessRun(String interactionType, String conversationId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("interactionType", interactionType);
        metadata.put("agentType", "react");
        enrichOriginMetadata(metadata, conversationId);
        return harnessRunService.startRun("react", conversationId, agentId, agentName, metadata).getId();
    }

    private void enrichOriginMetadata(Map<String, Object> metadata, String conversationId) {
        vip.mate.agent.context.ChatOrigin origin = vip.mate.agent.context.ChatOriginHolder.get();
        if (origin != null) {
            if (origin.requesterId() != null && !origin.requesterId().isBlank()) {
                metadata.put("requesterId", origin.requesterId());
            }
            if (origin.workspaceId() != null) {
                metadata.put("workspaceId", origin.workspaceId());
            }
            if (origin.workspaceBasePath() != null && !origin.workspaceBasePath().isBlank()) {
                metadata.put("workspaceBasePath", origin.workspaceBasePath());
            }
            if (origin.channelId() != null) {
                metadata.put("channelId", origin.channelId());
            }
            if (origin.invocationMetadata() != null && !origin.invocationMetadata().isEmpty()) {
                metadata.putAll(origin.invocationMetadata());
            }
        }
        if (conversationId != null && conversationId.startsWith("cron:")) {
            metadata.put("entrypoint", "cron");
        } else if (origin != null && origin.channelId() != null) {
            metadata.put("entrypoint", "channel");
        } else {
            metadata.put("entrypoint", "web");
        }
    }

    private Flux<NodeOutput> observeHarnessStream(Flux<NodeOutput> stream, String runId) {
        AtomicInteger sentEventCount = new AtomicInteger(0);
        AtomicReference<NodeOutput> lastOutput = new AtomicReference<>();
        AtomicBoolean finalized = new AtomicBoolean(false);
        return stream
                .doOnNext(output -> {
                    lastOutput.set(output);
                    List<GraphEventPublisher.GraphEvent> allEvents = GraphEventPublisher.extractEvents(output);
                    int newStart = sentEventCount.get();
                    if (newStart < allEvents.size()) {
                        harnessRunService.ingestEvents(runId, allEvents.subList(newStart, allEvents.size()));
                        sentEventCount.set(allEvents.size());
                    }
                    harnessRunService.updateSummary(runId, summary -> mergeSummary(summary, buildHarnessSummary(output)));
                })
                .doOnError(error -> {
                    if (finalized.compareAndSet(false, true)) {
                        harnessRunService.failRun(runId, error);
                    }
                })
                .doFinally(signalType -> {
                    if (!finalized.compareAndSet(false, true)) {
                        return;
                    }
                    NodeOutput output = lastOutput.get();
                    if (signalType == SignalType.CANCEL && !isTerminalOutput(output)) {
                        harnessRunService.interruptRun(runId, "cancelled");
                        return;
                    }
                    harnessRunService.completeRun(runId, buildHarnessSummary(output));
                });
    }

    private void completeHarnessRun(String runId, OverAllState state) {
        List<GraphEventPublisher.GraphEvent> events = state.<List<GraphEventPublisher.GraphEvent>>value(PENDING_EVENTS)
                .orElse(List.of());
        harnessRunService.ingestEvents(runId, events);
        harnessRunService.completeRun(runId, buildHarnessSummary(state));
    }

    private boolean isTerminalOutput(NodeOutput output) {
        if (output == null || output.state() == null) {
            return false;
        }
        if (hasFinalAnswer(output)) {
            return true;
        }
        if (Boolean.TRUE.equals(output.state().value(AWAITING_APPROVAL, false))) {
            return true;
        }
        return output.state().<String>value(FINISH_REASON)
                .filter(reason -> !reason.isBlank())
                .isPresent();
    }

    private HarnessExecutionSummary buildHarnessSummary(NodeOutput output) {
        HarnessExecutionSummary summary = new HarnessExecutionSummary();
        if (output == null || output.state() == null) {
            summary.setFinishReason("completed");
            return summary;
        }
        summary.setPromptTokens(output.state().value(PROMPT_TOKENS, 0));
        summary.setCompletionTokens(output.state().value(COMPLETION_TOKENS, 0));
        summary.setRuntimeModelName(output.state().value(RUNTIME_MODEL_NAME, ""));
        summary.setRuntimeProviderId(output.state().value(RUNTIME_PROVIDER_ID, ""));
        summary.setErrorMessage(output.state().<String>value(ERROR).orElse(null));
        summary.setFinalAnswerPreview(trimFinalAnswer(output.state().<String>value(FINAL_ANSWER).orElse(null)));
        summary.setFinishReason(resolveFinishReason(output.state().value(FINISH_REASON, ""),
                output.state().value(AWAITING_APPROVAL, false),
                output.state().value(LIMIT_EXCEEDED, false),
                hasFinalAnswer(output)));
        return summary;
    }

    private HarnessExecutionSummary buildHarnessSummary(OverAllState state) {
        HarnessExecutionSummary summary = new HarnessExecutionSummary();
        summary.setPromptTokens(state.value(PROMPT_TOKENS, 0));
        summary.setCompletionTokens(state.value(COMPLETION_TOKENS, 0));
        summary.setRuntimeModelName(state.value(RUNTIME_MODEL_NAME, ""));
        summary.setRuntimeProviderId(state.value(RUNTIME_PROVIDER_ID, ""));
        summary.setErrorMessage(state.<String>value(ERROR).orElse(null));
        summary.setFinalAnswerPreview(trimFinalAnswer(state.<String>value(FINAL_ANSWER).orElse(null)));
        boolean hasFinalAnswer = state.<String>value(FINAL_ANSWER).filter(answer -> !answer.isBlank()).isPresent();
        summary.setFinishReason(resolveFinishReason(state.value(FINISH_REASON, ""),
                state.value(AWAITING_APPROVAL, false),
                state.value(LIMIT_EXCEEDED, false),
                hasFinalAnswer));
        return summary;
    }

    private String resolveFinishReason(String finishReason, boolean awaitingApproval,
                                       boolean limitExceeded, boolean hasFinalAnswer) {
        if (finishReason != null && !finishReason.isBlank()) {
            return finishReason;
        }
        if (awaitingApproval) {
            return "awaiting_approval";
        }
        if (limitExceeded) {
            return "limit_exceeded";
        }
        if (hasFinalAnswer) {
            return "completed";
        }
        return "completed";
    }

    private void mergeSummary(HarnessExecutionSummary target, HarnessExecutionSummary source) {
        if (source.getPromptTokens() > 0) {
            target.setPromptTokens(source.getPromptTokens());
        }
        if (source.getCompletionTokens() > 0) {
            target.setCompletionTokens(source.getCompletionTokens());
        }
        if (source.getRuntimeModelName() != null && !source.getRuntimeModelName().isBlank()) {
            target.setRuntimeModelName(source.getRuntimeModelName());
        }
        if (source.getRuntimeProviderId() != null && !source.getRuntimeProviderId().isBlank()) {
            target.setRuntimeProviderId(source.getRuntimeProviderId());
        }
        if (source.getFinishReason() != null && !source.getFinishReason().isBlank()) {
            target.setFinishReason(source.getFinishReason());
        }
        if (source.getErrorMessage() != null && !source.getErrorMessage().isBlank()) {
            target.setErrorMessage(source.getErrorMessage());
        }
        if (source.getFinalAnswerPreview() != null && !source.getFinalAnswerPreview().isBlank()) {
            target.setFinalAnswerPreview(source.getFinalAnswerPreview());
        }
    }

    private String trimFinalAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String normalized = answer.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > 1600 ? normalized.substring(0, 1600) + "…" : normalized;
    }
}
