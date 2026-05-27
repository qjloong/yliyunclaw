package vip.mate.agent.graph.plan;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import vip.mate.agent.graph.plan.state.PlanStateKeys;
import vip.mate.agent.graph.state.MateClawStateKeys;
import vip.mate.agent.context.ConversationWindowManager;
import vip.mate.harness.model.HarnessExecutionSummary;
import vip.mate.harness.service.HarnessRunService;
import vip.mate.planning.service.PlanningService;
import vip.mate.system.service.SystemSettingService;
import vip.mate.teacher.model.TeacherTurnContext;
import vip.mate.teacher.service.TeacherIntentService;
import vip.mate.teacher.service.TeacherRulePackService;
import vip.mate.teacher.service.TeacherSkillDefinitionService;
import vip.mate.tool.image.vision.ImageVisionService;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.MessageEntity;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 StateGraph 的 Plan-Execute Agent
 * <p>
 * 使用 spring-ai-alibaba-graph-core 的 StateGraph 引擎实现：
 * <ol>
 *   <li>简单问答快速退出（PlanGenerationNode 前置判断）</li>
 *   <li>多步任务：规划 → 逐步执行（带工具调用）→ 汇总</li>
 * </ol>
 * <p>
 * content_delta 和 thinking_delta 由节点内 NodeStreamingChatHelper 直推，
 * chatStructuredStream() 只处理 phase/tool/plan/step 等结构化事件。
 * 不再从 NodeOutput 二次整段下发已流式推送的内容。
 *
 * @author MateClaw Team
 */
@Slf4j
public class StateGraphPlanExecuteAgent extends BaseAgent implements StructuredStreamCapable {

    private final CompiledGraph compiledGraph;
    private final PlanningService planningService;
    private final org.springframework.ai.chat.model.ChatModel chatModel;
    private final ConversationWindowManager conversationWindowManager;
    private final HarnessRunService harnessRunService;
    private final List<ToolCallback> toolCallbacks;
    private final SystemSettingService systemSettingService;
    private final AtomicReference<Long> currentWorkspaceId = new AtomicReference<>();
    private static final ObjectMapper TEACHER_OBJECT_MAPPER = new ObjectMapper();

    public StateGraphPlanExecuteAgent(ChatClient chatClient, ConversationService conversationService,
                                      CompiledGraph compiledGraph, PlanningService planningService,
                                      org.springframework.ai.chat.model.ChatModel chatModel,
                                      ConversationWindowManager conversationWindowManager,
                                      ImageVisionService imageVisionService,
                                      HarnessRunService harnessRunService) {
        this(chatClient, conversationService, compiledGraph, planningService, chatModel,
                conversationWindowManager, imageVisionService, harnessRunService, List.of(), null);
    }

    public StateGraphPlanExecuteAgent(ChatClient chatClient, ConversationService conversationService,
                                      CompiledGraph compiledGraph, PlanningService planningService,
                                      org.springframework.ai.chat.model.ChatModel chatModel,
                                      ConversationWindowManager conversationWindowManager,
                                      ImageVisionService imageVisionService,
                                      HarnessRunService harnessRunService,
                                      List<ToolCallback> toolCallbacks) {
        this(chatClient, conversationService, compiledGraph, planningService, chatModel,
                conversationWindowManager, imageVisionService, harnessRunService, toolCallbacks, null);
    }

    public StateGraphPlanExecuteAgent(ChatClient chatClient, ConversationService conversationService,
                                      CompiledGraph compiledGraph, PlanningService planningService,
                                      org.springframework.ai.chat.model.ChatModel chatModel,
                                      ConversationWindowManager conversationWindowManager,
                                      ImageVisionService imageVisionService,
                                      HarnessRunService harnessRunService,
                                      List<ToolCallback> toolCallbacks,
                                      SystemSettingService systemSettingService) {
        super(chatClient, conversationService, imageVisionService);
        this.compiledGraph = compiledGraph;
        this.planningService = planningService;
        this.chatModel = chatModel;
        this.conversationWindowManager = conversationWindowManager;
        this.harnessRunService = harnessRunService;
        this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : List.of();
        this.systemSettingService = systemSettingService;
    }

    @Override
    public Flux<AgentService.StreamDelta> chatStructuredStream(String userMessage, String conversationId) {
        return chatStructuredStream(userMessage, conversationId, "");
    }

    @Override
    public Flux<AgentService.StreamDelta> chatStructuredStream(String userMessage, String conversationId,
                                                                String requesterId) {
        setState(AgentState.RUNNING);
        captureCurrentWorkspaceId();
        String harnessRunId = startHarnessRun("chat_structured_stream", conversationId);
        try {
            log.info("[{}] Plan-Execute structured stream: conversationId={}", agentName, conversationId);
            TeacherWorkflowDecision teacherDecision = decideTeacherWorkflow(userMessage, conversationId);
            if (teacherDecision.shortCircuit()) {
                return teacherDecision.awaitingConfirmation()
                        ? teacherPlanResponseStream(teacherDecision.response(), harnessRunId)
                        : teacherDirectResponseStream(teacherDecision.response(), harnessRunId);
            }
            Map<String, Object> inputs = buildInitialState(teacherDecision.effectiveUserMessage(), conversationId);
            inputs.put(MateClawStateKeys.REQUESTER_ID, requesterId != null ? requesterId : "");
            return executeStream(inputs, harnessRunId);
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            setState(AgentState.ERROR);
            return Flux.error(e);
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
        captureCurrentWorkspaceId();
        String harnessRunId = startHarnessRun("chat_replay_stream", conversationId);
        try {
            log.info("[{}] Plan-Execute replay stream: conversationId={}", agentName, conversationId);
            Map<String, Object> inputs = buildInitialState(userMessage, conversationId);
            inputs.put(MateClawStateKeys.REQUESTER_ID, requesterId != null ? requesterId : "");

            // 从 DB 恢复 awaiting_approval 状态的计划上下文
            PlanningService.PlanResumeContext ctx = planningService.findAwaitingApprovalContext(conversationId);
            if (ctx != null) {
                inputs.put(PlanStateKeys.PLAN_ID, ctx.planId());
                inputs.put(PlanStateKeys.PLAN_STEPS, ctx.steps());
                inputs.put(PlanStateKeys.NEEDS_PLANNING, true);
                inputs.put(PlanStateKeys.PLAN_VALID, true);
                inputs.put(PlanStateKeys.CURRENT_STEP_INDEX, ctx.awaitingStepIndex());
                if (!ctx.completedResults().isEmpty()) {
                    inputs.put(PlanStateKeys.COMPLETED_RESULTS, ctx.completedResults());
                    // 重建 working context，包含历史消息和已完成步骤结果
                    @SuppressWarnings("unchecked")
                    List<Message> messages = (List<Message>) inputs.get(MateClawStateKeys.MESSAGES);
                    // messages 中最后一条是当前 UserMessage，去掉再算历史
                    List<Message> history = messages.size() > 1
                            ? messages.subList(0, messages.size() - 1) : List.of();
                    inputs.put(PlanStateKeys.WORKING_CONTEXT,
                            buildWorkingContext(history, ctx.completedResults()));
                }
                log.info("[{}] Replay: restored plan {} at step {}/{}", agentName,
                        ctx.planId(), ctx.awaitingStepIndex(), ctx.steps().size());
            } else {
                log.warn("[{}] Replay: no awaiting-approval plan found, falling back to fresh run", agentName);
            }

            // 注入预批准的工具调用，StepExecutionNode 匹配后跳过 ToolGuard
            if (toolCallPayload != null && !toolCallPayload.isEmpty()) {
                inputs.put(MateClawStateKeys.PRE_APPROVED_TOOL_CALL, toolCallPayload);
            }

            return executeStream(inputs, harnessRunId);
        } catch (Exception e) {
            harnessRunService.failRun(harnessRunId, e);
            setState(AgentState.ERROR);
            return Flux.error(e);
        }
    }

    /** 公共流执行逻辑，由 chatStructuredStream 和 chatWithReplayStream 共用 */
    private Flux<AgentService.StreamDelta> executeStream(Map<String, Object> inputs, String harnessRunId) {
        String threadId = UUID.randomUUID().toString();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        AtomicInteger sentEventCount = new AtomicInteger(0);
        AtomicInteger finalPromptTokens = new AtomicInteger(0);
        AtomicInteger finalCompletionTokens = new AtomicInteger(0);
        AtomicReference<String> finalModelName = new AtomicReference<>("");
        AtomicReference<String> finalProviderId = new AtomicReference<>("");
        // 去重：记录上一次已持久化的 step 结果和 thinking，防止 PlanSummaryNode 重复 emit 上一步内容
        AtomicReference<String> lastPersistedStepResult = new AtomicReference<>("");
        AtomicReference<String> lastPersistedStepThinking = new AtomicReference<>("");
        AtomicBoolean finalAnswerEmitted = new AtomicBoolean(false);
        AtomicBoolean finalThinkingEmitted = new AtomicBoolean(false);
        AtomicInteger harnessEventCount = new AtomicInteger(0);
        AtomicReference<com.alibaba.cloud.ai.graph.NodeOutput> lastOutput = new AtomicReference<>();
        AtomicBoolean harnessFinalized = new AtomicBoolean(false);

        return compiledGraph.stream(inputs, config)
                .flatMapIterable(output -> {
                    lastOutput.set(output);
                    List<GraphEventPublisher.GraphEvent> harnessEvents = GraphEventPublisher.extractEvents(output);
                    int harnessStart = harnessEventCount.get();
                    if (harnessStart < harnessEvents.size()) {
                        harnessRunService.ingestEvents(harnessRunId, harnessEvents.subList(harnessStart, harnessEvents.size()));
                        harnessEventCount.set(harnessEvents.size());
                    }
                    harnessRunService.updateSummary(harnessRunId,
                            summary -> mergeSummary(summary, buildHarnessSummary(output)));

                    List<AgentService.StreamDelta> deltas = new ArrayList<>();
                    // 1. 提取事件（只发送新增部分）
                    List<GraphEventPublisher.GraphEvent> allEvents = GraphEventPublisher.extractEvents(output);
                    int newStart = sentEventCount.get();
                    if (newStart < allEvents.size()) {
                        for (int i = newStart; i < allEvents.size(); i++) {
                            var event = allEvents.get(i);
                            deltas.add(AgentService.StreamDelta.event(event.type(), event.data()));
                        }
                        sentEventCount.set(allEvents.size());
                    }

                    // 2. 内容始终通过 StreamDelta 返回（用于持久化），已广播过的标记 persistOnly 避免重复推送
                    boolean contentAlreadyStreamed = output.state()
                            .value(MateClawStateKeys.CONTENT_STREAMED, false);
                    boolean thinkingAlreadyStreamed = output.state()
                            .value(MateClawStateKeys.THINKING_STREAMED, false);

                    // 2a. 各步骤执行结果（StepExecutionNode 已通过 NodeStreamingChatHelper 直推 SSE，
                    //     这里仅作为 persistOnly 送入 Accumulator，确保写入 mate_message）
                    //     利用内容本身去重，避免 PlanSummaryNode 输出时重复 emit 上一步残留在 state 的值
                    output.state().<String>value(PlanStateKeys.CURRENT_STEP_RESULT)
                            .filter(s -> !s.isEmpty())
                            .filter(s -> !s.equals(lastPersistedStepResult.get()))
                            .ifPresent(stepContent -> {
                                deltas.add(AgentService.StreamDelta.persistOnly(stepContent, null));
                                lastPersistedStepResult.set(stepContent);
                            });

                    output.state().<String>value(PlanStateKeys.CURRENT_STEP_THINKING)
                            .filter(s -> !s.isEmpty())
                            .filter(s -> !s.equals(lastPersistedStepThinking.get()))
                            .ifPresent(stepThinking -> {
                                deltas.add(AgentService.StreamDelta.persistOnly(null, stepThinking));
                                lastPersistedStepThinking.set(stepThinking);
                            });

                        // 2b. 最终回答 / 最终汇总
                        String finalAnswer = output.state().<String>value(PlanStateKeys.FINAL_SUMMARY)
                            .filter(s -> !s.isEmpty())
                            .orElseGet(() -> output.state().<String>value(PlanStateKeys.DIRECT_ANSWER)
                                .filter(s -> !s.isEmpty())
                                .orElse(""));
                        if (!finalAnswer.isEmpty() && finalAnswerEmitted.compareAndSet(false, true)) {
                        deltas.add(contentAlreadyStreamed
                            ? AgentService.StreamDelta.persistOnly(finalAnswer, null)
                            : new AgentService.StreamDelta(finalAnswer, null));
                        }

                        output.state().<String>value(PlanStateKeys.FINAL_SUMMARY_THINKING)
                            .filter(s -> !s.isEmpty())
                            .filter(thinking -> finalThinkingEmitted.compareAndSet(false, true))
                            .ifPresent(thinking -> deltas.add(thinkingAlreadyStreamed
                                ? AgentService.StreamDelta.persistOnly(null, thinking)
                                : new AgentService.StreamDelta(null, thinking)));

                    // 3. 更新最新累计 token usage
                    finalPromptTokens.set(output.state().value(MateClawStateKeys.PROMPT_TOKENS, 0));
                    finalCompletionTokens.set(output.state().value(MateClawStateKeys.COMPLETION_TOKENS, 0));
                    finalModelName.set(output.state().value(MateClawStateKeys.RUNTIME_MODEL_NAME, ""));
                    finalProviderId.set(output.state().value(MateClawStateKeys.RUNTIME_PROVIDER_ID, ""));

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
                    log.error("[{}] Plan-Execute stream error: {}", agentName, e.getMessage());
                    setState(AgentState.ERROR);
                })
                .doFinally(signalType -> {
                    if (!harnessFinalized.compareAndSet(false, true)) {
                        return;
                    }
                    if (signalType == SignalType.ON_ERROR) {
                        return;
                    }
                    com.alibaba.cloud.ai.graph.NodeOutput output = lastOutput.get();
                    if (signalType == SignalType.CANCEL && !isTerminalOutput(output)) {
                        harnessRunService.interruptRun(harnessRunId, "cancelled");
                        return;
                    }
                    harnessRunService.completeRun(harnessRunId, buildHarnessSummary(output));
                });
    }

    @Override
    public String chat(String userMessage, String conversationId) {
        // 委托到 chatStructuredStream，过滤事件，拼接内容
        return chatStructuredStream(userMessage, conversationId)
                .filter(delta -> !delta.isEvent() && delta.content() != null)
                .map(AgentService.StreamDelta::content)
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block();
    }

    @Override
    public Flux<String> chatStream(String userMessage, String conversationId) {
        // 委托到 chatStructuredStream，过滤事件，只保留内容
        return chatStructuredStream(userMessage, conversationId)
                .filter(delta -> !delta.isEvent() && delta.content() != null)
                .map(AgentService.StreamDelta::content);
    }

    @Override
    public String chatWithReplay(String userMessage, String conversationId, String toolCallPayload) {
        return chatWithReplayStream(userMessage, conversationId, toolCallPayload)
                .filter(delta -> !delta.isEvent() && delta.content() != null)
                .map(AgentService.StreamDelta::content)
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block();
    }

    @Override
    public String execute(String goal, String conversationId) {
        // 同 chat()，走同一套 Plan-Execute Graph
        return chat(goal, conversationId);
    }

    private Map<String, Object> buildInitialState(String userMessage, String conversationId) {
        // 加载会话历史（复用 BaseAgent.buildConversationHistory，与 ReAct 对齐）
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
        messages.add(buildCurrentUserMessage(conversationId, userMessage));

        // 构建 working context：对历史消息做受控长度摘要
        String workingContext = buildWorkingContext(historyMessages, List.of());

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(PlanStateKeys.GOAL, userMessage);
        inputs.put(MateClawStateKeys.SYSTEM_PROMPT,
                systemPrompt != null ? systemPrompt : "你是一个有帮助的AI助手。");
        inputs.put(MateClawStateKeys.CONVERSATION_ID, conversationId);
        inputs.put(MateClawStateKeys.AGENT_ID, agentId != null ? agentId : "");
        vip.mate.agent.context.ChatOrigin origin = vip.mate.agent.context.ChatOriginHolder.get();
        String effectiveWorkspaceBasePath = origin.workspaceBasePath() != null && !origin.workspaceBasePath().isBlank()
            ? origin.workspaceBasePath()
            : workspaceBasePath;
        inputs.put(MateClawStateKeys.WORKSPACE_BASE_PATH, effectiveWorkspaceBasePath != null ? effectiveWorkspaceBasePath : "");
        // 注入会话消息（复用 MateClawStateKeys.MESSAGES，与 ReAct 一致）
        inputs.put(MateClawStateKeys.MESSAGES, messages);
        // 注入 working context
        inputs.put(PlanStateKeys.WORKING_CONTEXT, workingContext);
        inputs.put(PlanStateKeys.CURRENT_STEP_INDEX, 0);
        inputs.put(MateClawStateKeys.CONTENT_STREAMED, false);
        inputs.put(MateClawStateKeys.THINKING_STREAMED, false);
        inputs.put(MateClawStateKeys.STREAMED_CONTENT, "");
        inputs.put(MateClawStateKeys.STREAMED_THINKING, "");
        inputs.put(MateClawStateKeys.REQUESTER_ID, "");
        inputs.put(MateClawStateKeys.PROMPT_TOKENS, 0);
        inputs.put(MateClawStateKeys.COMPLETION_TOKENS, 0);
        inputs.put(MateClawStateKeys.RUNTIME_MODEL_NAME, modelName != null ? modelName : "");
        inputs.put(MateClawStateKeys.RUNTIME_PROVIDER_ID, runtimeProviderId != null ? runtimeProviderId : "");
        inputs.put(MateClawStateKeys.TRACE_ID, UUID.randomUUID().toString().substring(0, 8));

        // RFC-063r §2.5: same as ReAct path — enrich and store the ChatOrigin
        // so StepExecutionNode (and any sub-graphs spawned via DelegateAgentTool)
        // can read it back from state.
        Long parsedAgentIdForOrigin = null;
        try { parsedAgentIdForOrigin = agentId != null ? Long.valueOf(agentId) : null; } catch (Exception ignored) {}
        if (parsedAgentIdForOrigin != null) {
            origin = origin.withAgent(parsedAgentIdForOrigin);
        }
        origin = origin.withConversationId(conversationId)
                .withWorkspace(origin.workspaceId(), effectiveWorkspaceBasePath);
        inputs.put(MateClawStateKeys.CHAT_ORIGIN, origin);
        return inputs;
    }

    /**
     * 构建受控长度的 working context。
     * <p>
     * 将会话历史 + 已完成步骤结果压缩为结构化摘要块，
     * 避免 prompt 随对话和步骤执行无限膨胀。
     * <p>
     * 规则：
     * <ul>
     *   <li>历史消息：保留最近 MAX_HISTORY_MESSAGES 条，每条截断至 MAX_MSG_CHARS 字符</li>
     *   <li>步骤结果：保留最近 MAX_STEP_RESULTS 条，每条截断至 MAX_STEP_CHARS 字符</li>
     *   <li>总体截断至 MAX_CONTEXT_CHARS 字符</li>
     * </ul>
     */
    static String buildWorkingContext(List<Message> historyMessages, List<String> completedResults) {
        StringBuilder sb = new StringBuilder();

        // 历史消息摘要
        if (historyMessages != null && !historyMessages.isEmpty()) {
            sb.append("=== 对话历史摘要 ===\n");
            int startIdx = Math.max(0, historyMessages.size() - MAX_HISTORY_MESSAGES);
            for (int i = startIdx; i < historyMessages.size(); i++) {
                Message msg = historyMessages.get(i);
                String role = msg.getMessageType().name().toLowerCase();
                String content = msg.getText();
                if (content != null && !content.isEmpty()) {
                    String truncated = content.length() > MAX_MSG_CHARS
                            ? content.substring(0, MAX_MSG_CHARS) + "…" : content;
                    sb.append("[").append(role).append("] ").append(truncated).append("\n");
                }
            }
            sb.append("\n");
        }

        // 已完成步骤结果摘要
        if (completedResults != null && !completedResults.isEmpty()) {
            sb.append("=== 已完成步骤结果 ===\n");
            int startIdx = Math.max(0, completedResults.size() - MAX_STEP_RESULTS);
            for (int i = startIdx; i < completedResults.size(); i++) {
                String result = completedResults.get(i);
                String truncated = result.length() > MAX_STEP_CHARS
                        ? result.substring(0, MAX_STEP_CHARS) + "…" : result;
                sb.append(truncated).append("\n");
            }
        }

        // 总体截断
        String context = sb.toString();
        if (context.length() > MAX_CONTEXT_CHARS) {
            context = context.substring(0, MAX_CONTEXT_CHARS) + "\n…（上下文已截断）";
        }
        return context;
    }

    // Working context 长度控制参数
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int MAX_MSG_CHARS = 500;
    private static final int MAX_STEP_RESULTS = 5;
    private static final int MAX_STEP_CHARS = 800;
    private static final int MAX_CONTEXT_CHARS = 6000;

    private String startHarnessRun(String interactionType, String conversationId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("interactionType", interactionType);
        metadata.put("agentType", "plan_execute");
        if (isTeacherAgent()) {
            metadata.put("templateId", templateId);
            metadata.put("profileId", profileId);
            metadata.put("capabilityPackId", capabilityPackId);
            metadata.put("teacherWorkflow", true);
        }
        enrichOriginMetadata(metadata, conversationId);
        return harnessRunService.startRun("plan", conversationId, agentId, agentName, metadata).getId();
    }

    private TeacherWorkflowDecision decideTeacherWorkflow(String userMessage, String conversationId) {
        if (!isTeacherAgent() || !ConversationService.RUNTIME_MODE_PLAN.equals(runtimeMode)) {
            return TeacherWorkflowDecision.continueWith(userMessage);
        }
        String text = userMessage != null ? userMessage.trim() : "";
        if (text.isEmpty()) {
            return TeacherWorkflowDecision.continueWith(userMessage);
        }

        boolean awaitingConfirmation = hasAwaitingTeacherPlan(conversationId);
        boolean explicitLocalScope = hasExplicitLocalDirectoryScope(text) || hasTeacherLocalScope(conversationId);
        TeacherTurnContext turnContext = TeacherIntentService.buildContext(agentId, agentName, templateId, profileId,
                capabilityPackId, runtimeMode, text, hasBoundKnowledgeBases(), explicitLocalScope, awaitingConfirmation);
        if (TeacherIntentService.isIdentityOrUsageQuestion(text)) {
            return TeacherWorkflowDecision.respondDirect(TeacherIntentService.buildIdentityAnswer(turnContext));
        }
        if (awaitingConfirmation && isTeacherPlanConfirmation(text)) {
            return TeacherWorkflowDecision.continueWith(buildConfirmedTeacherExamPrompt(text, explicitLocalScope, conversationId));
        }
        if (awaitingConfirmation) {
            return TeacherWorkflowDecision.respondAwaitingConfirmation(buildTeacherPlanResponse(text, true, explicitLocalScope));
        }
        if (!awaitingConfirmation && isTeacherExamIntent(text)) {
            return TeacherWorkflowDecision.respondAwaitingConfirmation(buildTeacherPlanResponse(text, false, explicitLocalScope));
        }
        if (explicitLocalScope) {
            return TeacherWorkflowDecision.continueWith(buildTeacherLocalDirectoryPriorityPrompt(userMessage));
        }
        return TeacherWorkflowDecision.continueWith(userMessage);
    }

    private Flux<AgentService.StreamDelta> teacherPlanResponseStream(String response, String harnessRunId) {
        HarnessExecutionSummary summary = new HarnessExecutionSummary();
        summary.setFinishReason("awaiting_teacher_confirmation");
        summary.setFinalAnswerPreview(trimFinalAnswer(response));
        harnessRunService.completeRun(harnessRunId, summary);
        setState(AgentState.IDLE);
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("state", "awaiting_confirmation");
        workflow.put("templateId", TeacherIntentService.TEMPLATE_ID);
        workflow.put("workflow", "teacher_exam");
        workflow.put("requiresUserConfirmation", true);
        workflow.put("localScope", response != null && response.contains("teacher_exam_local_scope:explicit")
                ? "explicit" : "none");
        return Flux.just(
                AgentService.StreamDelta.event("teacher_workflow", workflow),
                new AgentService.StreamDelta(response, null)
        );
    }

    private Flux<AgentService.StreamDelta> teacherDirectResponseStream(String response, String harnessRunId) {
        HarnessExecutionSummary summary = new HarnessExecutionSummary();
        summary.setFinishReason("teacher_direct_answer");
        summary.setFinalAnswerPreview(trimFinalAnswer(response));
        harnessRunService.completeRun(harnessRunId, summary);
        setState(AgentState.IDLE);
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("state", "direct_answer");
        workflow.put("templateId", TeacherIntentService.TEMPLATE_ID);
        workflow.put("workflow", "teacher_exam");
        workflow.put("requiresUserConfirmation", false);
        return Flux.just(
                AgentService.StreamDelta.event("teacher_workflow", workflow),
                new AgentService.StreamDelta(response, null)
        );
    }

    private boolean isTeacherAgent() {
        return TeacherIntentService.isTeacherAgent(templateId, profileId, capabilityPackId);
    }

    private boolean hasAwaitingTeacherPlan(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        List<MessageEntity> history = conversationService.listRecentMessages(conversationId, 12);
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntity msg = history.get(i);
            String metadata = msg.getMetadata();
            if (metadata != null && metadata.contains("\"teacherWorkflow\"")) {
                if (metadata.contains("\"state\":\"awaiting_confirmation\"")
                        || metadata.contains("\"state\": \"awaiting_confirmation\"")) {
                    return true;
                }
                if (metadata.contains("\"state\":\"generating_exam\"")
                        || metadata.contains("\"state\": \"generating_exam\"")
                        || metadata.contains("\"state\":\"completed\"")
                        || metadata.contains("\"state\": \"completed\"")) {
                    return false;
                }
            }
            String text = msg.getContent();
            if (text != null && text.contains("teacher_exam_plan_state:awaiting_confirmation")) {
                return true;
            }
            if (text != null && text.contains("teacher_exam_plan_state:completed")) {
                return false;
            }
            if (text != null && looksLikeCompletedTeacherExam(text)) {
                return false;
            }
        }
        return false;
    }

    private boolean looksLikeCompletedTeacherExam(String text) {
        String normalized = text != null ? text : "";
        return normalized.contains("## 试题")
                && (normalized.contains("## 参考答案") || normalized.contains("## 答案"))
                && (normalized.contains("## 采分点") || normalized.contains("## 评分标准"));
    }

    private boolean isTeacherPlanConfirmation(String text) {
        return TeacherIntentService.isTeacherPlanConfirmation(text);
    }

    private boolean isTeacherExamIntent(String text) {
        return TeacherIntentService.isTeacherExamIntent(text);
    }

    private boolean hasExplicitLocalDirectoryScope(String text) {
        return TeacherIntentService.hasExplicitLocalDirectoryScope(text);
    }

    private boolean hasTeacherLocalScope(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        List<MessageEntity> history = conversationService.listRecentMessages(conversationId, 12);
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntity msg = history.get(i);
            String metadata = msg.getMetadata();
            if (metadata != null && metadata.contains("\"teacherWorkflow\"")
                    && (metadata.contains("\"localScope\":\"explicit\"")
                    || metadata.contains("\"localScope\": \"explicit\""))) {
                return true;
            }
            String text = msg.getContent();
            if (text != null && text.contains("teacher_exam_local_scope:explicit")) {
                return true;
            }
            if (text != null && text.contains("teacher_exam_plan_state:completed")) {
                return false;
            }
        }
        return false;
    }

    private String buildTeacherLocalDirectoryPriorityPrompt(String userMessage) {
        return """
                用户已明确指定本地目录作为上下文范围。请将该目录视为首要上下文边界，并优先执行稳定本地链路：
                1. 先复用 <context-router> 中的 Cached material index / Project cues；如果里面已有可用目录索引，不要再为同一工作区或同一大目录调用 `list_directory` / `index_directory_materials`。
                2. 只有当缓存缺失、用户明确要求刷新/同步、或需要具体子目录/文件列表时，才对指定目录使用目录工具；工具返回 cachePolicy.stopRepeatingTraversal=true 后必须停止重扫。
                3. 若当前 runtime 已暴露搜索/筛选 skill 或 tool，先用它缩小候选；否则使用 `filter_directory_materials(...)` 做轻量筛选。
                4. 只按当前任务目标读取少量必要文件、页段或片段，不要把整批教材全文一次性塞入上下文。
                5. 不要先要求用户导入知识库；只有当本地链路因越权、越界或权限阻断而无法继续，或缓存/索引明确缺少材料时，才提出审批或知识库导入/绑定作为后备路径。
                6. 不要通过 shell 反复试错、盲扫目录、重建同一索引或手工猜测文件名。

                以下是用户原始请求，请完整执行其中的业务目标与目录范围：
                %s
                """.formatted(userMessage != null ? userMessage : "");
    }

    private String buildTeacherPlanResponse(String userMessage, boolean revision, boolean explicitLocalScope) {
        boolean hasKnowledge = hasBoundKnowledgeBases();
        String module = TeacherIntentService.detectBusinessModule(userMessage);
        String rulePackId = TeacherRulePackService.rulePackIdForModule(module);
        String moduleLabel = teacherModuleLabel(module);
        String questionTypeSummary = teacherQuestionTypeSummary(module);
        String focusSummary = teacherFocusSummary(module);
        String intro = revision ? "已根据你的补充要求更新命题方案。未确认前我不会生成完整试题。" : "下面先给出命题方案。未确认前我不会生成完整试题。";
        boolean asksLatestStandards = asksTeacherLatestStandards(userMessage);
        String sourceNote = explicitLocalScope
                ? "用户已明确指定本地目录/工作区范围。正式执行时应将该目录作为首要上下文边界，优先复用上下文中的缓存目录索引；只有缓存缺失、用户要求刷新或需要具体子目录时才走稳定本地链路（list/index -> 现有搜索/筛选能力 -> filter fallback -> 按需读取/抽取）。目录工具命中缓存或返回 stopRepeatingTraversal 后不再重扫同一大目录；知识库只作为补充或后备路径。"
                : hasKnowledge
                ? "优先使用当前 Agent 已绑定知识库与本轮会话材料；正式出题时会标注来源依据。若你要求最新教材、最新课标或名著稿件，我会优先使用知识库中标记为最新版本的资料，并在来源依据中说明。"
                : asksLatestStandards
                ? "当前 Agent 未绑定知识库，但本次需求涉及最新教材、课标或名著稿件。正式出题前必须先上传、处理并绑定相关知识库，或在会话中补充材料；未补充前只能给方案和材料准备清单，不能假称已按最新课标生成。"
                : "当前 Agent 未绑定知识库。若需要基于教材/课标/本地资料出题，应先上传材料并绑定知识库，或在会话中补充材料；需要直接读取工作区目录时会按权限和缓存策略处理。";
        String localScopeMarker = explicitLocalScope ? "<!-- teacher_exam_local_scope:explicit -->\n" : "";
        return """
                %s<!-- teacher_exam_plan_state:awaiting_confirmation -->
                <!-- teacher_exam_module:%s -->
                <!-- teacher_rule_pack_id:%s -->
                ## 命题方案（待确认）

                %s

                ### 使用范围
                - 任务需求：%s
                - 业务模块：%s
                - 来源范围：%s

                ### 题量与题型
                - 题量：按你的需求执行；若未指定，默认 5 道。
                - 题型：%s

                ### 考点与难度
                - 考点：%s
                - 难度：默认包含基础、提升两个层级；如需拓展题可在确认前补充。

                ### 分值与输出
                - 每题标注题型、考点、难度和建议分值。
                - 正式生成后不论业务模块是名著、文言文、现代文、古诗词、基础知识还是写作，都必须按统一 Teacher v2 结构分块输出：试题、参考答案、采分点、来源依据；命题说明和质量审核仅放内部折叠区。

                ### 需要确认
                请回复“确认出题”开始正式生成；也可以继续修改题量、难度、题型、材料/篇目范围或采分要求。
                """.formatted(localScopeMarker, module, rulePackId, intro, sanitizeTeacherLine(userMessage),
                moduleLabel, sourceNote, questionTypeSummary, focusSummary);
    }

    private String buildConfirmedTeacherExamPrompt(String userMessage, boolean explicitLocalScope, String conversationId) {
        String rulePackId = resolveTeacherRulePackId(userMessage, conversationId);
        String module = resolveTeacherModule(userMessage, conversationId);
        String localScopeRule = explicitLocalScope
                ? "7. 用户已明确指定本地目录作为上下文范围。请先复用 <context-router> 的 Cached material index；只有缓存缺失、用户要求刷新/同步或需要具体子目录时才对该目录走稳定本地链路（list/index -> 现有搜索/筛选能力 -> filter fallback -> 按需读取/抽取），不要先要求知识库导入。\n8. 如果目录工具返回 cachePolicy.stopRepeatingTraversal=true 或 cacheHit=true，必须基于缓存索引选择候选文件继续，不要再次遍历同一大目录。\n9. 只有当该目录超出当前工作区边界、需要越权访问、本地读取被权限阻断，或缓存索引确认缺少必要材料时，才发起审批或退回知识库导入/绑定建议。"
                : "7. 对当前工作区内的资料，先复用缓存目录索引；需要具体文件时再按需列子目录/读取文件，不要每轮重扫工作区，不要猜文件名。目录工具返回 cacheHit 或 stopRepeatingTraversal 后必须停止重扫。需要越权访问时先发起审批。";
        String assemblyRule = "paper_assembly".equals(module)
                ? """
                8. 这是组卷任务。主 Agent 必须先拆分模块，再合并成一套卷子：模块题块可包含名著、文言文、现代文、古诗词、基础知识、写作；每个模块先按对应 RulePack 生成结构化题块；最后统一合并卷面、参考答案、采分点和来源依据。
                9. 第一版组卷不启用复杂 subagent UI；不得把多个模块输出成互不关联的多条回答，必须形成一份完整试卷。
                10. 卷面必须包含标题、适用年级/范围、总分、模块分值、题号连续编号；答案和采分点按题号对应。
                """
                : "";
        return """
                <!-- teacher_exam_plan_state:completed -->
                用户已确认上一轮命题方案。请进入正式出题阶段。

                用户确认/补充：%s

                当前必须执行的规则包：
                %s

                当前必须遵循的 Teacher Skill：
                %s

                严格要求：
                1. 不要再次停留在待确认方案；直接生成正式结果。
                2. 不论当前规则包是名著、文言文、现代文、古诗词、基础知识还是写作，都必须使用同一套 Teacher v2 交付协议，前端才能分块展示、展开收起和导出。
                3. 优先输出一个 JSON 对象，type 固定为 "teacher_exam_result_v2"，字段保持：
                   {
                     "type": "teacher_exam_result_v2",
                     "module": "paper_assembly|classic_reading|classical_chinese|modern_reading|ancient_poetry|basic_knowledge|writing|unknown",
                     "paper": { "title": "", "grade": "", "scenario": "", "totalScore": 0 },
                     "questions": [],
                     "answers": [],
                     "scoringRubric": [],
                     "sources": [],
                     "internalReview": [],
                     "exportOptions": { "questionsOnly": true, "full": true }
                   }
                4. questions 只放题目、题型、考点、难度、分值、必要材料和选项，不要混入答案。文言文/现代文的原文材料也放在 questions 对应题目前，不另起非标准主区块。
                5. answers 只放参考答案；scoringRubric 只放采分点和分值；sources 只放来源依据；internalReview 放命题说明和命题质量审核，仅供展开查看，不进入客户主展示和完整版导出。
                6. 如果不能稳定输出 JSON，则必须按以下 Markdown 标题分块输出，标题文本保持一致：
                   ## 试题
                   ## 参考答案
                   ## 采分点
                   ## 命题质量审核
                   ## 来源依据
                7. 如果用户要求依据“最新教材”“最新课标”“名著稿件”等资料，但当前缺少已绑定知识库或会话材料，不要编造原文细节或假称已按最新课标出题；仍按结构化字段或以上分块输出，在“试题”中写明无法基于材料生成的原因，在“来源依据”中给出需要导入/绑定知识库、标记资料类型或补充材料的替代路径。
                %s
                %s
                """.formatted(sanitizeTeacherLine(userMessage),
                buildTeacherRulePackPromptRules(rulePackId, module),
                TeacherSkillDefinitionService.activeSkillPromptRules(),
                localScopeRule,
                assemblyRule);
    }

    private String buildClassicReadingPromptRules() {
        loadWorkspaceRulePackOverrideIfNeeded();
        return TeacherRulePackService.classicReadingPromptRules(currentWorkspaceId.get());
    }

    private String buildTeacherRulePackPromptRules(String rulePackId) {
        loadWorkspaceRulePackOverrideIfNeeded(rulePackId);
        return TeacherRulePackService.promptRules(rulePackId, currentWorkspaceId.get());
    }

    private String buildTeacherRulePackPromptRules(String rulePackId, String module) {
        if ("paper_assembly".equals(module)) {
            return String.join("\n\n",
                    TeacherRulePackService.promptRules(TeacherRulePackService.CLASSIC_READING_V2_ID, currentWorkspaceId.get()),
                    TeacherRulePackService.promptRules(TeacherRulePackService.CLASSICAL_CHINESE_V1_ID, currentWorkspaceId.get()),
                    TeacherRulePackService.promptRules(TeacherRulePackService.MODERN_READING_V1_ID, currentWorkspaceId.get()),
                    TeacherRulePackService.promptRules(TeacherRulePackService.ANCIENT_POETRY_V1_ID, currentWorkspaceId.get()),
                    TeacherRulePackService.promptRules(TeacherRulePackService.BASIC_KNOWLEDGE_V1_ID, currentWorkspaceId.get()),
                    TeacherRulePackService.promptRules(TeacherRulePackService.WRITING_V1_ID, currentWorkspaceId.get()));
        }
        return buildTeacherRulePackPromptRules(rulePackId);
    }

    private void loadWorkspaceRulePackOverrideIfNeeded() {
        loadWorkspaceRulePackOverrideIfNeeded(TeacherRulePackService.CLASSIC_READING_V2_ID);
    }

    private void loadWorkspaceRulePackOverrideIfNeeded(String rulePackId) {
        Long workspaceId = currentWorkspaceId.get();
        if (workspaceId == null || systemSettingService == null
                || TeacherRulePackService.hasWorkspaceOverride(rulePackId, workspaceId)) {
            return;
        }
        String key = TeacherRulePackService.WORKSPACE_OVERRIDE_SETTING_PREFIX
                + workspaceId + "." + rulePackId;
        String stored = systemSettingService.getRawValue(key, "");
        if (stored == null || stored.isBlank()) {
            return;
        }
        try {
            TeacherRulePackService.putWorkspaceOverride(workspaceId,
                    TEACHER_OBJECT_MAPPER.readValue(stored, vip.mate.teacher.model.TeacherRulePack.class));
        } catch (Exception ignored) {
            // Invalid override falls back to global/built-in rules.
        }
    }

    private String resolveTeacherRulePackId(String userMessage, String conversationId) {
        String module = resolveTeacherModule(userMessage, conversationId);
        if (!"unknown".equals(module)) {
            return TeacherRulePackService.rulePackIdForModule(module);
        }
        return TeacherRulePackService.CLASSIC_READING_V2_ID;
    }

    private String resolveTeacherModule(String userMessage, String conversationId) {
        String module = TeacherIntentService.detectBusinessModule(userMessage);
        if (!"unknown".equals(module)) {
            return module;
        }
        return resolveTeacherPlanModule(conversationId);
    }

    private String resolveTeacherPlanModule(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "classic_reading";
        }
        List<MessageEntity> history = conversationService.listRecentMessages(conversationId, 12);
        if (history == null || history.isEmpty()) {
            return "classic_reading";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            String text = history.get(i).getContent();
            if (text == null) {
                continue;
            }
            for (String module : List.of("paper_assembly", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing", "classic_reading")) {
                if (text.contains("teacher_exam_module:" + module)) {
                    return module;
                }
            }
        }
        return "classic_reading";
    }

    private String teacherModuleLabel(String module) {
        return switch (module) {
            case "paper_assembly" -> "初中语文综合组卷";
            case "classical_chinese" -> "初中文言文阅读";
            case "modern_reading" -> "初中现代文阅读";
            case "ancient_poetry" -> "初中古诗词鉴赏";
            case "basic_knowledge" -> "初中语文基础知识";
            case "writing" -> "初中写作训练";
            default -> "初中名著阅读";
        };
    }

    private String teacherQuestionTypeSummary(String module) {
        return switch (module) {
            case "paper_assembly" -> "先拆分为名著、文言文、现代文、古诗词、基础知识、写作等模块题块，再合并成一套卷子。";
            case "classical_chinese" -> "优先覆盖文言字词、句子翻译、断句停顿、内容理解、主旨情感、对比迁移。";
            case "modern_reading" -> "优先覆盖信息提取、内容概括、语言赏析、结构作用、主旨情感、拓展任务。";
            case "ancient_poetry" -> "优先覆盖默写理解、意象情感、炼字赏句、手法分析、比较阅读。";
            case "basic_knowledge" -> "优先覆盖字音字形、词语运用、病句修改、文学文化常识、综合性学习。";
            case "writing" -> "优先覆盖审题立意、提纲设计、素材选择、片段写作、升格修改。";
            default -> "优先覆盖填空、选择、简答、分析、探究等适合名著阅读的题型，微写作只作附加。";
        };
    }

    private String teacherFocusSummary(String module) {
        return switch (module) {
            case "paper_assembly" -> "总分、模块分值、题号连续、跨模块难度梯度、答案采分点对应关系和来源依据。";
            case "classical_chinese" -> "文言实词虚词、句式翻译、断句停顿、内容理解、主旨情感和初中范围内的文化常识。";
            case "modern_reading" -> "文本类型、信息提取、内容概括、人物/语言/结构赏析、主旨情感和基于文本的迁移表达。";
            case "ancient_poetry" -> "课标篇目、诗句理解、意象情感、语言赏析、常见表现手法和比较阅读。";
            case "basic_knowledge" -> "初中常见字词、语病、语用、文学文化常识和可评分的综合实践任务。";
            case "writing" -> "审题边界、立意方向、结构提纲、素材匹配、片段表达和评分维度。";
            default -> "人物、情节、主题、阅读理解与表达迁移。";
        };
    }

    private boolean asksTeacherLatestStandards(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        return userMessage.matches("(?s).*(最新教材|新教材|最新版教材|课程标准|课标|名著稿件|教材稿件|按教材|按课标).*");
    }

    private void captureCurrentWorkspaceId() {
        vip.mate.agent.context.ChatOrigin origin = vip.mate.agent.context.ChatOriginHolder.get();
        currentWorkspaceId.set(origin != null ? origin.workspaceId() : null);
    }

    private boolean hasBoundKnowledgeBases() {
        if (knowledgeBaseIdsJson == null || knowledgeBaseIdsJson.isBlank()) {
            return false;
        }
        String trimmed = knowledgeBaseIdsJson.trim();
        return !trimmed.equals("[]") && !trimmed.equals("{}");
    }

    private String sanitizeTeacherLine(String value) {
        if (value == null || value.isBlank()) {
            return "未补充具体限制。";
        }
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() > 600 ? normalized.substring(0, 600) + "..." : normalized;
    }

    private record TeacherWorkflowDecision(boolean shortCircuit, String response, String effectiveUserMessage,
                                           boolean awaitingConfirmation) {
        static TeacherWorkflowDecision respondAwaitingConfirmation(String response) {
            return new TeacherWorkflowDecision(true, response, response, true);
        }
        static TeacherWorkflowDecision respondDirect(String response) {
            return new TeacherWorkflowDecision(true, response, response, false);
        }
        static TeacherWorkflowDecision continueWith(String userMessage) {
            return new TeacherWorkflowDecision(false, "", userMessage, false);
        }
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

    private boolean isTerminalOutput(com.alibaba.cloud.ai.graph.NodeOutput output) {
        if (output == null || output.state() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(output.state().value(MateClawStateKeys.AWAITING_APPROVAL, false))) {
            return true;
        }
        if (output.state().<String>value(PlanStateKeys.FINAL_SUMMARY).filter(s -> !s.isBlank()).isPresent()) {
            return true;
        }
        return output.state().<String>value(PlanStateKeys.DIRECT_ANSWER)
                .filter(s -> !s.isBlank())
                .isPresent();
    }

    private HarnessExecutionSummary buildHarnessSummary(com.alibaba.cloud.ai.graph.NodeOutput output) {
        HarnessExecutionSummary summary = new HarnessExecutionSummary();
        if (output == null || output.state() == null) {
            summary.setFinishReason("completed");
            return summary;
        }
        summary.setPromptTokens(output.state().value(MateClawStateKeys.PROMPT_TOKENS, 0));
        summary.setCompletionTokens(output.state().value(MateClawStateKeys.COMPLETION_TOKENS, 0));
        summary.setRuntimeModelName(output.state().value(MateClawStateKeys.RUNTIME_MODEL_NAME, ""));
        summary.setRuntimeProviderId(output.state().value(MateClawStateKeys.RUNTIME_PROVIDER_ID, ""));
        summary.setFinalAnswerPreview(resolveFinalAnswerPreview(output));
        summary.setFinishReason(resolveFinishReason(output));
        return summary;
    }

    private String resolveFinalAnswerPreview(com.alibaba.cloud.ai.graph.NodeOutput output) {
        if (output == null || output.state() == null) {
            return null;
        }
        String answer = output.state().<String>value(PlanStateKeys.FINAL_SUMMARY).orElse(null);
        if (answer == null || answer.isBlank()) {
            answer = output.state().<String>value(PlanStateKeys.DIRECT_ANSWER).orElse(null);
        }
        return trimFinalAnswer(answer);
    }

    private String resolveFinishReason(com.alibaba.cloud.ai.graph.NodeOutput output) {
        if (output == null || output.state() == null) {
            return "completed";
        }
        if (Boolean.TRUE.equals(output.state().value(MateClawStateKeys.AWAITING_APPROVAL, false))) {
            return "awaiting_approval";
        }
        if (output.state().<String>value(PlanStateKeys.FINAL_SUMMARY).filter(s -> !s.isBlank()).isPresent()) {
            return "completed";
        }
        if (output.state().<String>value(PlanStateKeys.DIRECT_ANSWER).filter(s -> !s.isBlank()).isPresent()) {
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
