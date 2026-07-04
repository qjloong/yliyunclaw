package vip.mate.teacher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.agent.interceptor.AgentExecutionInterceptor;
import vip.mate.plugin.api.agent.AgentContext;
import vip.mate.plugin.api.agent.AgentPromptAugmenter;
import vip.mate.teacher.model.TeacherTurnContext;
import vip.mate.teacher.service.TeacherIntentService;
import vip.mate.teacher.service.TeacherRulePackService;
import vip.mate.teacher.service.TeacherSkillDefinitionService;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.MessageEntity;

import java.util.*;

/**
 * Teacher-agent extension — implements {@link AgentExecutionInterceptor}
 * and {@link AgentPromptAugmenter}. All teacher business logic lives here,
 * with zero invasive changes to the core agent framework.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherAgentExtension implements AgentExecutionInterceptor, AgentPromptAugmenter, Ordered {

    private final ConversationService conversationService;

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    // ═══════════════════════════════════════
    // AgentPromptAugmenter — build-time injection
    // ═══════════════════════════════════════

    @Override
    public boolean supports(AgentContext context) {
        return TeacherIntentService.isTeacherAgent(
                context.templateId(), context.profileId(),
                context.capabilityPackId(), context.pluginKey());
    }

    @Override
    public String augmentSystemPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        String skillRules = TeacherSkillDefinitionService.activeSkillPromptRules();
        if (!skillRules.isBlank()) sb.append("\n\n## Teacher Exam Skills\n").append(skillRules);
        String rulePrompt = TeacherRulePackService.promptRules(TeacherRulePackService.CLASSIC_READING_V2_ID);
        if (!rulePrompt.isBlank()) sb.append("\n\n## Teacher Exam Rules\n").append(rulePrompt);
        return sb.toString();
    }

    // ═══════════════════════════════════════
    // AgentExecutionInterceptor — per-turn routing
    // ═══════════════════════════════════════

    @Override
    public String transformMessage(String userMessage, String conversationId, AgentContext context) {
        if (!supports(context)) return userMessage;
        String text = userMessage != null ? userMessage.trim() : "";
        if (text.isEmpty()) return userMessage;

        boolean awaitingConfirmation = hasAwaitingTeacherPlan(conversationId);
        boolean explicitLocalScope = hasExplicitLocalDirectoryScope(text)
                || hasTeacherLocalScope(conversationId);

        if (awaitingConfirmation && TeacherIntentService.isTeacherPlanConfirmation(text)) {
            return buildConfirmedTeacherExamPrompt(text, explicitLocalScope, conversationId, context);
        }
        if (explicitLocalScope && !awaitingConfirmation) {
            return buildTeacherLocalDirectoryPriorityPrompt(userMessage);
        }
        return userMessage;
    }

    @Override
    public Optional<Flux<AgentService.StreamDelta>> beforeExecution(
            String userMessage, String conversationId, AgentContext context) {
        if (!supports(context)) return Optional.empty();
        String text = userMessage != null ? userMessage.trim() : "";
        if (text.isEmpty()) return Optional.empty();

        boolean awaitingConfirmation = hasAwaitingTeacherPlan(conversationId);
        boolean explicitLocalScope = hasExplicitLocalDirectoryScope(text)
                || hasTeacherLocalScope(conversationId);
        TeacherTurnContext turnCtx = TeacherIntentService.buildContext(
                context.agentId(), context.agentName(), context.templateId(),
                context.profileId(), context.capabilityPackId(),
                context.runtimeMode(), text, hasBoundKnowledgeBases(context),
                explicitLocalScope, awaitingConfirmation);

        if (TeacherIntentService.isIdentityOrUsageQuestion(text)) {
            return Optional.of(teacherDirectResponseStream(
                    TeacherIntentService.buildIdentityAnswer(turnCtx)));
        }
        if (!awaitingConfirmation && TeacherIntentService.isTeacherExamIntent(text)) {
            String plan = buildTeacherPlanResponse(text, false, explicitLocalScope, conversationId, context);
            return Optional.of(teacherPlanResponseStream(plan));
        }
        if (awaitingConfirmation && !TeacherIntentService.isTeacherPlanConfirmation(text)) {
            String plan = buildTeacherPlanResponse(text, true, explicitLocalScope, conversationId, context);
            return Optional.of(teacherPlanResponseStream(plan));
        }
        return Optional.empty();
    }

    // ═══════════════════════════════════════
    // Stream responses
    // ═══════════════════════════════════════

    private Flux<AgentService.StreamDelta> teacherDirectResponseStream(String response) {
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("state", "direct_answer");
        wf.put("templateId", TeacherIntentService.TEMPLATE_ID);
        wf.put("workflow", "teacher_exam");
        return Flux.just(AgentService.StreamDelta.event("teacher_workflow", wf),
                new AgentService.StreamDelta(response, null));
    }

    private Flux<AgentService.StreamDelta> teacherPlanResponseStream(String response) {
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("state", "awaiting_confirmation");
        wf.put("templateId", TeacherIntentService.TEMPLATE_ID);
        wf.put("workflow", "teacher_exam");
        wf.put("requiresUserConfirmation", true);
        wf.put("localScope", response != null && response.contains("teacher_exam_local_scope:explicit")
                ? "explicit" : "none");
        return Flux.just(AgentService.StreamDelta.event("teacher_workflow", wf),
                new AgentService.StreamDelta(response, null));
    }

    // ═══════════════════════════════════════
    // History / context helpers
    // ═══════════════════════════════════════

    private boolean hasAwaitingTeacherPlan(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        List<MessageEntity> history = conversationService.listRecentMessages(conversationId, 12);
        if (history == null || history.isEmpty()) return false;
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntity msg = history.get(i);
            String md = msg.getMetadata();
            if (md != null && md.contains("\"teacherWorkflow\"")) {
                if (md.contains("\"state\":\"awaiting_confirmation\"") || md.contains("\"state\": \"awaiting_confirmation\"")) return true;
                if (md.contains("\"state\":\"generating_exam\"") || md.contains("\"state\": \"generating_exam\"")
                        || md.contains("\"state\":\"completed\"") || md.contains("\"state\": \"completed\"")) return false;
            }
            String txt = msg.getContent();
            if (txt != null && txt.contains("teacher_exam_plan_state:awaiting_confirmation")) return true;
            if (txt != null && txt.contains("teacher_exam_plan_state:completed")) return false;
        }
        return false;
    }

    private static boolean hasExplicitLocalDirectoryScope(String text) {
        return TeacherIntentService.hasExplicitLocalDirectoryScope(text);
    }

    private boolean hasTeacherLocalScope(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        List<MessageEntity> history = conversationService.listRecentMessages(conversationId, 12);
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntity msg = history.get(i);
            String md = msg.getMetadata();
            if (md != null && md.contains("\"teacherWorkflow\"")
                    && (md.contains("\"localScope\":\"explicit\"") || md.contains("\"localScope\": \"explicit\""))) return true;
            String txt = msg.getContent();
            if (txt != null && txt.contains("teacher_exam_local_scope:explicit")) return true;
            if (txt != null && txt.contains("teacher_exam_plan_state:completed")) return false;
        }
        return false;
    }

    // ═══════════════════════════════════════
    // Plan response builders
    // ═══════════════════════════════════════

    private String buildTeacherPlanResponse(String userMessage, boolean revision,
                                             boolean explicitLocalScope, String conversationId, AgentContext context) {
        boolean hasKnowledge = hasBoundKnowledgeBases(context);
        String module = revision ? resolveTeacherModule(userMessage, conversationId)
                : TeacherIntentService.detectBusinessModule(userMessage);
        String rulePackId = TeacherRulePackService.rulePackIdForModule(module);
        String intro = revision ? "已根据你的补充要求更新命题方案。" : "下面先给出命题方案。";
        String sourceNote = explicitLocalScope ? "用户已明确指定本地目录/工作区范围。"
                : hasKnowledge ? "优先使用已绑定知识库与会话材料。" : "当前 Agent 未绑定知识库。";
        String localScopeMarker = explicitLocalScope ? "<!-- teacher_exam_local_scope:explicit -->\n" : "";
        return String.format("""
                %s<!-- teacher_exam_plan_state:awaiting_confirmation -->
                <!-- teacher_exam_module:%s --><!-- teacher_rule_pack_id:%s -->
                ## 命题方案（待确认）
                %s
                ### 使用范围
                - 任务需求：%s - 业务模块：%s - 来源范围：%s
                ### 题量与题型
                - 题量：按需求执行；默认 5 道。- 题型：%s
                ### 考点与难度
                - 考点：%s - 难度：基础+提升
                ### 需要确认
                请回复"确认出题"开始正式生成。
                """,
                localScopeMarker, module, rulePackId, intro,
                sanitizeLine(userMessage), teacherModuleLabel(module), sourceNote,
                teacherQuestionTypeSummary(module), teacherFocusSummary(module));
    }

    private String buildConfirmedTeacherExamPrompt(String userMessage, boolean explicitLocalScope,
                                                    String conversationId, AgentContext context) {
        String rulePackId = resolveTeacherRulePackId(userMessage, conversationId);
        String module = resolveTeacherModule(userMessage, conversationId);
        String localScopeRule = explicitLocalScope
                ? "用户已明确指定本地目录。先复用缓存索引；只有缓存缺失时才读取。"
                : "先复用缓存目录索引；需要时再按需读取。";
        return String.format("""
                <!-- teacher_exam_plan_state:completed -->
                用户已确认上一轮命题方案。请进入正式出题阶段。
                用户确认/补充：%s
                当前规则包：%s
                当前 Teacher Skill：%s
                严格要求：
                1. 不要再次停留在待确认方案；直接生成正式结果。
                2. 使用 Teacher v2 交付协议，JSON type=teacher_exam_result_v2。
                3. questions 只放正式卷面题号、题干、材料、选项和分值。
                4. answers/scoringRubric/sources 携带对应 questionNo。
                5. 缺少材料时不编造原文细节。
                %s
                """,
                sanitizeLine(userMessage),
                buildTeacherRulePackPromptRules(rulePackId, module),
                TeacherSkillDefinitionService.activeSkillPromptRules(),
                localScopeRule);
    }

    private String buildTeacherRulePackPromptRules(String rulePackId, String module) {
        if ("paper_assembly".equals(module)) {
            return String.join("\n\n",
                    TeacherRulePackService.promptRules(TeacherRulePackService.CLASSIC_READING_V2_ID),
                    TeacherRulePackService.promptRules(TeacherRulePackService.CLASSICAL_CHINESE_V1_ID),
                    TeacherRulePackService.promptRules(TeacherRulePackService.MODERN_READING_V1_ID),
                    TeacherRulePackService.promptRules(TeacherRulePackService.ANCIENT_POETRY_V1_ID),
                    TeacherRulePackService.promptRules(TeacherRulePackService.BASIC_KNOWLEDGE_V1_ID),
                    TeacherRulePackService.promptRules(TeacherRulePackService.WRITING_V1_ID));
        }
        return TeacherRulePackService.promptRules(rulePackId);
    }

    private String buildTeacherLocalDirectoryPriorityPrompt(String userMessage) {
        return String.format("""
                用户已明确指定本地目录作为上下文范围。
                1. 先复用上下文中的 Cached material index。
                2. 只有缓存缺失或用户要求刷新时才使用目录工具。
                3. 不要先要求用户导入知识库。
                以下是用户原始请求：%s
                """, userMessage != null ? userMessage : "");
    }

    // ═══════════════════════════════════════
    // Module helpers
    // ═══════════════════════════════════════

    static String teacherModuleLabel(String m) { return switch (m) {
        case "paper_assembly" -> "初中语文综合组卷"; case "classical_chinese" -> "初中文言文阅读";
        case "modern_reading" -> "初中现代文阅读"; case "ancient_poetry" -> "初中古诗词鉴赏";
        case "basic_knowledge" -> "初中语文基础知识"; case "writing" -> "初中写作训练";
        default -> "初中名著阅读"; }; }
    static String teacherQuestionTypeSummary(String m) { return switch (m) {
        case "paper_assembly" -> "先拆模块再合并卷子。"; case "classical_chinese" -> "文言字词、翻译、断句、主旨。";
        case "modern_reading" -> "信息提取、概括、赏析、主旨情感。"; case "ancient_poetry" -> "默写、意象、炼字、手法。";
        case "basic_knowledge" -> "字词、语病、常识。"; case "writing" -> "审题、提纲、素材、片段。";
        default -> "填空、选择、简答、分析、探究。"; }; }
    static String teacherFocusSummary(String m) { return switch (m) {
        case "paper_assembly" -> "总分、模块分值、题号连续。"; case "classical_chinese" -> "实词虚词、句式、主旨。";
        case "modern_reading" -> "文本类型、信息提取、赏析。"; case "ancient_poetry" -> "课标篇目、意象情感。";
        case "basic_knowledge" -> "常见字词、语病、语用。"; case "writing" -> "审题边界、立意方向、评分维度。";
        default -> "人物、情节、主题、阅读表达。"; }; }

    private String resolveTeacherModule(String userMessage, String conversationId) {
        String m = TeacherIntentService.detectBusinessModule(userMessage);
        if (!"unknown".equals(m)) return m;
        if (conversationId != null) {
            List<MessageEntity> h = conversationService.listRecentMessages(conversationId, 12);
            for (int i = h.size() - 1; i >= 0; i--) {
                String t = h.get(i).getContent();
                if (t != null) for (String mod : List.of("paper_assembly", "classical_chinese", "modern_reading",
                        "ancient_poetry", "basic_knowledge", "writing", "classic_reading"))
                    if (t.contains("teacher_exam_module:" + mod)) return mod;
            }
        }
        return "classic_reading";
    }

    private String resolveTeacherRulePackId(String userMessage, String conversationId) {
        String m = resolveTeacherModule(userMessage, conversationId);
        return "unknown".equals(m) ? TeacherRulePackService.CLASSIC_READING_V2_ID
                : TeacherRulePackService.rulePackIdForModule(m);
    }

    // ═══════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════

    private static boolean hasBoundKnowledgeBases(AgentContext context) {
        String json = context.knowledgeBaseIdsJson();
        if (json == null || json.isBlank()) return false;
        String t = json.trim();
        return !t.equals("[]") && !t.equals("{}");
    }

    private static String sanitizeLine(String v) {
        if (v == null || v.isBlank()) return "未补充具体限制。";
        String n = v.replaceAll("[\\r\\n]+", " ").trim();
        return n.length() > 600 ? n.substring(0, 600) + "..." : n;
    }
}
