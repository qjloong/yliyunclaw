package vip.mate.agent.graph.plan;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import vip.mate.tool.image.vision.ImageVisionService;
import vip.mate.workspace.conversation.ConversationService;

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

    public StateGraphPlanExecuteAgent(ChatClient chatClient, ConversationService conversationService,
                                      CompiledGraph compiledGraph, PlanningService planningService,
                                      org.springframework.ai.chat.model.ChatModel chatModel,
                                      ConversationWindowManager conversationWindowManager,
                                      ImageVisionService imageVisionService,
                                      HarnessRunService harnessRunService) {
        super(chatClient, conversationService, imageVisionService);
        this.compiledGraph = compiledGraph;
        this.planningService = planningService;
        this.chatModel = chatModel;
        this.conversationWindowManager = conversationWindowManager;
        this.harnessRunService = harnessRunService;
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
            log.info("[{}] Plan-Execute structured stream: conversationId={}", agentName, conversationId);
            TeacherWorkflowDecision teacherDecision = decideTeacherWorkflow(userMessage, conversationId);
            if (teacherDecision.shortCircuit()) {
                return teacherPlanResponseStream(teacherDecision.response(), harnessRunId);
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
            historyMessages = conversationWindowManager.fitToWindow(
                    historyMessages,
                    systemPrompt != null ? systemPrompt : "",
                    userMessage,
                    maxInputTokens,
                    chatModel,
                    conversationId,
                    parsedAgentId);
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

        List<Message> history = buildConversationHistory(conversationId, userMessage);
        boolean awaitingConfirmation = hasAwaitingTeacherPlan(history);
        boolean explicitLocalScope = hasExplicitLocalDirectoryScope(text) || hasTeacherLocalScope(history);
        if (awaitingConfirmation && isTeacherPlanConfirmation(text)) {
            return TeacherWorkflowDecision.continueWith(buildConfirmedTeacherExamPrompt(text, explicitLocalScope));
        }
        if (awaitingConfirmation) {
            return TeacherWorkflowDecision.respond(buildTeacherPlanResponse(text, true, explicitLocalScope));
        }
        if (!awaitingConfirmation && isTeacherExamIntent(text)) {
            return TeacherWorkflowDecision.respond(buildTeacherPlanResponse(text, false, explicitLocalScope));
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
        return Flux.just(
                AgentService.StreamDelta.event("teacher_workflow", Map.of(
                        "state", "awaiting_confirmation",
                        "templateId", "builtin.teacher_exam_assistant"
                )),
                new AgentService.StreamDelta(response, null)
        );
    }

    private boolean isTeacherAgent() {
        return "builtin.teacher_exam_assistant".equals(templateId)
                || "teacher_exam_assistant_profile".equals(profileId)
                || "capability.education.junior_classics_exam".equals(capabilityPackId);
    }

    private boolean hasAwaitingTeacherPlan(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int start = Math.max(0, history.size() - 8);
        for (int i = history.size() - 1; i >= start; i--) {
            Message msg = history.get(i);
            String text = msg.getText();
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
        String normalized = text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
        return normalized.matches(".*(确认|同意|可以|开始出题|正式出题|按方案|生成试题|继续生成|confirm|go ahead|proceed).*");
    }

    private boolean isTeacherExamIntent(String text) {
        String normalized = text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
        if (normalized.matches(".*(你能做什么|怎么使用|如何使用|介绍|说明|解释|总结|梳理|分析考点|使用教程).*")) {
            return false;
        }
        return normalized.matches(".*(出题|命题|组卷|试卷|练习题|检测题|阅读题|选择题|填空题|简答题|探究题|仿题|仿写|生成.*题|题目|题型|采分点|评分标准|答案|中考题|课堂练习|单元检测|模拟考).*");
    }

    private boolean hasExplicitLocalDirectoryScope(String text) {
        String normalized = text != null ? text.trim() : "";
        if (normalized.isEmpty()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.matches(".*([a-z]:\\\\[^\n\r\t\"'，。；、 ]+|/[\\w./-]+|~[/\\\\][^\n\r\t\"'，。；、 ]+).*")) {
            return true;
        }
        return lower.matches(".*((在|按|根据|围绕|限定|只看|仅看|基于).{0,24}(目录|文件夹|路径|工作区|workspace).{0,60}(下|内|里面|范围|作为上下文|作为范围|作为来源)).*")
                || lower.matches(".*((目录|文件夹|路径|工作区|workspace).{0,40}(作为上下文|作为范围|作为来源|优先读取|优先使用)).*");
    }

    private boolean hasTeacherLocalScope(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int start = Math.max(0, history.size() - 8);
        for (int i = history.size() - 1; i >= start; i--) {
            Message msg = history.get(i);
            String text = msg.getText();
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
                1. 先复用 <context-router> 中的 Cached material index / Project cues；不要为同一工作区或同一大目录反复调用 `list_directory`。
                2. 只有当缓存缺失、用户明确要求刷新/同步、或需要具体子目录/文件列表时，才对指定目录使用 `list_directory` 或 `index_directory_materials`，不要先猜文件名。
                3. 若当前 runtime 已暴露搜索/筛选 skill 或 tool，先用它缩小候选；否则使用 `filter_directory_materials(...)` 做轻量筛选。
                4. 只按当前任务目标读取少量必要文件、页段或片段，不要把整批教材全文一次性塞入上下文。
                5. 不要先要求用户导入知识库；只有当本地链路因越权、越界或权限阻断而无法继续时，才提出审批或知识库导入/绑定作为后备路径。
                6. 不要通过 shell 反复试错、盲扫目录或手工猜测文件名。

                以下是用户原始请求，请完整执行其中的业务目标与目录范围：
                %s
                """.formatted(userMessage != null ? userMessage : "");
    }

    private String buildTeacherPlanResponse(String userMessage, boolean revision, boolean explicitLocalScope) {
        boolean hasKnowledge = hasBoundKnowledgeBases();
        String intro = revision ? "已根据你的补充要求更新命题方案。未确认前我不会生成完整试题。" : "下面先给出命题方案。未确认前我不会生成完整试题。";
        String sourceNote = explicitLocalScope
                ? "用户已明确指定本地目录/工作区范围。正式执行时应将该目录作为首要上下文边界，优先复用上下文中的缓存目录索引；只有缓存缺失、用户要求刷新或需要具体子目录时才走稳定本地链路（list/index -> 现有搜索/筛选能力 -> filter fallback -> 按需读取/抽取）；知识库只作为补充或后备路径。"
                : hasKnowledge
                ? "优先使用当前 Agent 已绑定知识库与本轮会话材料；正式出题时会标注来源依据。"
                : "当前 Agent 未绑定知识库。若需要基于教材/本地目录资料出题，可直接先在当前工作区内走安全目录浏览/按需读取链路；对于需要反复复用的资料，再考虑导入并绑定知识库。";
        String localScopeMarker = explicitLocalScope ? "<!-- teacher_exam_local_scope:explicit -->\n" : "";
        return """
                %s<!-- teacher_exam_plan_state:awaiting_confirmation -->
                ## 命题方案（待确认）

                %s

                ### 使用范围
                - 任务需求：%s
                - 来源范围：%s

                ### 题量与题型
                - 题量：按你的需求执行；若未指定，默认 5 道。
                - 题型：优先覆盖填空、选择、简答、探究等适合名著阅读的题型。

                ### 考点与难度
                - 考点：人物、情节、主题、阅读理解与表达迁移。
                - 难度：默认包含基础、提升两个层级；如需拓展题可在确认前补充。

                ### 分值与输出
                - 每题标注题型、考点、难度和建议分值。
                - 正式生成后分块输出：试题、参考答案、采分点、命题质量审核、来源依据。

                ### 需要确认
                请回复“确认出题”开始正式生成；也可以继续修改题量、难度、题型、名著范围或采分要求。
                """.formatted(localScopeMarker, intro, sanitizeTeacherLine(userMessage), sourceNote);
    }

    private String buildConfirmedTeacherExamPrompt(String userMessage, boolean explicitLocalScope) {
        String localScopeRule = explicitLocalScope
                ? "6. 用户已明确指定本地目录作为上下文范围。请先复用 <context-router> 的 Cached material index；只有缓存缺失、用户要求刷新/同步或需要具体子目录时才对该目录走稳定本地链路（list/index -> 现有搜索/筛选能力 -> filter fallback -> 按需读取/抽取），不要先要求知识库导入。\n7. 只有当该目录超出当前工作区边界、需要越权访问或本地读取被权限阻断时，才发起审批或退回知识库导入/绑定建议。"
                : "6. 对当前工作区内的资料，先复用缓存目录索引；需要具体文件时再按需列子目录/读取文件，不要每轮重扫工作区，不要猜文件名。需要越权访问时先发起审批。";
        return """
                <!-- teacher_exam_plan_state:completed -->
                用户已确认上一轮命题方案。请进入正式出题阶段。

                用户确认/补充：%s

                严格要求：
                1. 不要再次停留在待确认方案；直接生成正式结果。
                2. 必须按以下 Markdown 分块输出，标题文本保持一致；不要把答案、采分点、审核混入“试题”块：
                   ## 试题
                   ## 参考答案
                   ## 采分点
                   ## 命题质量审核
                   ## 来源依据
                3. 试题块优先，只放题目本身和必要的题目元信息。
                4. 参考答案、采分点、审核、来源依据分块写清楚，避免混在一段长文本里。
                5. 如果缺少已绑定知识库或会话材料，不要编造原文细节；仍按以上分块输出，在“试题”块写明无法基于材料生成的原因，在“来源依据”块给出需要导入/绑定知识库或补充材料的替代路径。
                %s
                """.formatted(sanitizeTeacherLine(userMessage), localScopeRule);
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

    private record TeacherWorkflowDecision(boolean shortCircuit, String response, String effectiveUserMessage) {
        static TeacherWorkflowDecision respond(String response) {
            return new TeacherWorkflowDecision(true, response, response);
        }
        static TeacherWorkflowDecision continueWith(String userMessage) {
            return new TeacherWorkflowDecision(false, "", userMessage);
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
