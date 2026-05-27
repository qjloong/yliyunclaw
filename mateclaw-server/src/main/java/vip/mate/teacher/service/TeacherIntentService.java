package vip.mate.teacher.service;

import vip.mate.teacher.model.TeacherTurnContext;

import java.util.Locale;

/**
 * Centralized v2 Teacher intent heuristics.
 *
 * <p>These checks deliberately stay deterministic and conservative. They only
 * decide whether a request is a Teacher identity/help/exam-planning turn; the
 * model still handles the substantive answer.</p>
 */
public final class TeacherIntentService {

    public static final String TEMPLATE_ID = "builtin.teacher_exam_assistant";
    public static final String PROFILE_ID = "teacher_exam_assistant_profile";
    public static final String CAPABILITY_PACK_ID = "capability.education.junior_chinese_exam";
    public static final String LEGACY_CAPABILITY_PACK_ID = "capability.education.junior_classics_exam";

    private TeacherIntentService() {
    }

    public static boolean isTeacherAgent(String templateId, String profileId, String capabilityPackId) {
        return TEMPLATE_ID.equals(templateId)
                || PROFILE_ID.equals(profileId)
                || CAPABILITY_PACK_ID.equals(capabilityPackId)
                || LEGACY_CAPABILITY_PACK_ID.equals(capabilityPackId);
    }

    public static TeacherTurnContext buildContext(String agentId,
                                                  String agentName,
                                                  String templateId,
                                                  String profileId,
                                                  String capabilityPackId,
                                                  String runtimeMode,
                                                  String userMessage,
                                                  boolean hasBoundKnowledgeBases,
                                                  boolean explicitLocalScope,
                                                  boolean awaitingConfirmation) {
        String normalized = normalize(userMessage);
        return new TeacherTurnContext(
                agentId,
                agentName,
                templateId,
                profileId,
                capabilityPackId,
                runtimeMode,
                detectBusinessModule(normalized),
                detectTaskType(normalized),
                hasBoundKnowledgeBases,
                explicitLocalScope,
                awaitingConfirmation
        );
    }

    public static boolean isIdentityOrUsageQuestion(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.matches(".*(你是谁|你是什么|你叫什么|你的身份|你能做什么|你可以做什么|你会做什么|怎么使用你|如何使用你|使用教程|快速开始|开始使用|介绍一下你|介绍你的能力).*")
                || normalized.matches(".*\\b(who are you|what can you do|how to use you|how do i use you)\\b.*");
    }

    public static boolean isTeacherPlanConfirmation(String text) {
        String normalized = normalize(text);
        return normalized.matches(".*(确认|同意|可以|开始出题|正式出题|按方案|生成试题|继续生成|confirm|go ahead|proceed).*");
    }

    public static boolean isTeacherExamIntent(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank() || isTeacherNonExamQuestion(normalized)) {
            return false;
        }
        return normalized.matches(".*(出题|命题|组卷|试卷|练习题|检测题|阅读题|选择题|填空题|简答题|探究题|仿题|仿写|生成.*题|题目|题型|采分点|评分标准|答案|中考题|课堂练习|单元检测|模拟考).*");
    }

    public static boolean hasExplicitLocalDirectoryScope(String text) {
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

    public static String buildIdentityAnswer(TeacherTurnContext context) {
        String displayName = context.agentName() != null && !context.agentName().isBlank()
                ? context.agentName()
                : "名师出题助手";
        String sourceState = context.hasBoundKnowledgeBases()
                ? "当前已绑定知识库，我会优先基于这些资料和本轮会话材料回答与出题。"
                : "当前没有绑定知识库；涉及具体教材、原文或资料细节时，我会先提示补充材料，避免凭空编造。";
        String localState = context.explicitLocalScope()
                ? "你也指定了本地目录或工作区范围，我会优先复用已有资料索引，必要时再按权限请求读取。"
                : "如果需要使用本地资料，建议先上传材料或绑定知识库；需要直接读取目录时会按工作区权限处理。";
        return """
                我是%s，当前作为 Teacher Agent 运行，主要面向初中语文教学出题与备考场景。

                我可以帮助你：
                - 解释名著、文言文、现代文阅读相关考点和材料；
                - 按年级、范围、题型、难度和分值设计命题方案；
                - 在 plan 模式下先给命题方案，等你确认后再正式生成试题；
                - 正式出题时分开输出试题、参考答案、采分点、来源依据和必要的质量审核。

                %s
                %s

                你可以直接告诉我：年级、材料范围、题量、题型、难度、是否需要答案和采分点。
                """.formatted(displayName, sourceState, localState).trim();
    }

    private static boolean isTeacherNonExamQuestion(String normalized) {
        return normalized.matches(".*(你能做什么|怎么使用|如何使用|使用教程|快速开始|介绍|说明|解释|总结|梳理|分析考点).*")
                && !normalized.matches(".*(出题|命题|组卷|生成.*题|试卷|练习题|检测题).*");
    }

    private static String detectTaskType(String normalized) {
        if (isIdentityOrUsageQuestion(normalized)) {
            return "identity_or_usage";
        }
        if (isTeacherExamIntent(normalized)) {
            return "exam_generation";
        }
        if (normalized.matches(".*(总结|概括|梳理).*")) {
            return "summary";
        }
        if (normalized.matches(".*(解释|分析|考点).*")) {
            return "explanation";
        }
        return "general";
    }

    public static String detectBusinessModule(String normalized) {
        normalized = normalize(normalized);
        if (normalized.matches(".*(组卷|一套卷|套卷|整卷|完整试卷|综合试卷|综合卷|模拟卷|期末卷|单元卷|月考卷).*")) {
            return "paper_assembly";
        }
        if (normalized.matches(".*(文言文|古文|实词|虚词|翻译|断句).*")) {
            return "classical_chinese";
        }
        if (normalized.matches(".*(现代文|记叙文|说明文|议论文|散文|小说阅读|新闻阅读|非连续性文本).*")) {
            return "modern_reading";
        }
        if (normalized.matches(".*(古诗|诗词|诗歌鉴赏|默写|炼字|赏句|意象|诗句|唐诗|宋词).*")) {
            return "ancient_poetry";
        }
        if (normalized.matches(".*(基础知识|字音|字形|成语|词语运用|病句|文学常识|文化常识|综合性学习|口语交际).*")) {
            return "basic_knowledge";
        }
        if (normalized.matches(".*(作文|写作|审题|立意|提纲|素材|片段写作|升格|修改作文|范文).*")) {
            return "writing";
        }
        if (normalized.matches(".*(名著|西游记|水浒传|骆驼祥子|朝花夕拾|昆虫记|经典常谈|钢铁是怎样炼成的|红星照耀中国|海底两万里).*")) {
            return "classic_reading";
        }
        return "unknown";
    }

    private static String normalize(String text) {
        return text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
    }
}
