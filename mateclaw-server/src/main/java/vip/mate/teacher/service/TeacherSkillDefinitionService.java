package vip.mate.teacher.service;

import vip.mate.teacher.model.TeacherSkillDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Built-in Teacher skill definitions.
 *
 * <p>This is the phase-6 foundation for controlled self-improvement. Future
 * drafts can propose changes to these skill definitions, but published runtime
 * changes must still go through administrator review.</p>
 */
public final class TeacherSkillDefinitionService {

    public static final String CLASSIC_EXAM_LOOP_ID = "teacher.skill.classic_exam_loop.v1";
    public static final String EXAM_REVIEW_ID = "teacher.skill.exam_review.v1";
    public static final String DEFAULT_BINDING_SETTING_KEY = "teacher.skill.bindings.default";

    private static final TeacherSkillDefinition CLASSIC_EXAM_LOOP = new TeacherSkillDefinition(
            CLASSIC_EXAM_LOOP_ID,
            "名著阅读命题闭环",
            "1.0.0",
            "classic_reading",
            "按规则包完成命题方案、用户确认、正式出题、答案采分点和来源审核。",
            List.of(
                    TeacherRulePackService.CLASSIC_READING_V2_ID,
                    TeacherRulePackService.CLASSICAL_CHINESE_V1_ID,
                    TeacherRulePackService.MODERN_READING_V1_ID,
                    TeacherRulePackService.ANCIENT_POETRY_V1_ID,
                    TeacherRulePackService.BASIC_KNOWLEDGE_V1_ID,
                    TeacherRulePackService.WRITING_V1_ID
            ),
            List.of(
                    "识别任务类型、业务模块、材料范围、题量、题型、难度和材料来源。",
                    "在 plan 模式下先输出命题方案并等待用户确认。",
                    "确认后按 RulePack 生成结构化试题、参考答案、采分点、来源依据和内部审核。",
                    "若材料不足，先提示导入知识库、上传材料或明确授权读取，不编造原文细节。",
                    "将生成结果交给命题质量审核 Skill 做一致性复核。"
            ),
            Map.of(
                    "required", List.of("taskIntent"),
                    "optional", List.of("grade", "semester", "bookScope", "questionCount", "questionTypes", "difficulty", "knowledgeBaseIds", "sessionMaterials"),
                    "materialPolicy", "knowledge_or_session_material_first"
            ),
            Map.of(
                    "type", "teacher_exam_result_v2",
                    "requiredSections", List.of("questions", "answers", "scoringRubric", "sources", "internalReview")
            ),
            List.of(
                    Map.of("id", "plan_gate", "label", "Plan 确认", "rule", "出题类任务在 plan 模式下必须先给方案，确认后再生成。"),
                    Map.of("id", "structured_result", "label", "结构化结果", "rule", "正式结果必须包含试题、答案、采分点、来源和审核。"),
                    Map.of("id", "no_fabrication", "label", "不编造材料", "rule", "缺少材料时给出补充路径，不编造原文或情节细节。")
            )
    );

    private static final TeacherSkillDefinition EXAM_REVIEW = new TeacherSkillDefinition(
            EXAM_REVIEW_ID,
            "命题质量审核",
            "1.0.0",
            "generic_exam_review",
            "对已生成试题进行规则一致性、答案采分点、材料来源和教师可用性复核。",
            List.of(
                    TeacherRulePackService.CLASSIC_READING_V2_ID,
                    TeacherRulePackService.CLASSICAL_CHINESE_V1_ID,
                    TeacherRulePackService.MODERN_READING_V1_ID,
                    TeacherRulePackService.ANCIENT_POETRY_V1_ID,
                    TeacherRulePackService.BASIC_KNOWLEDGE_V1_ID,
                    TeacherRulePackService.WRITING_V1_ID
            ),
            List.of(
                    "检查题型比例、分值和难度是否符合 RulePack。",
                    "检查主观题是否有参考答案和可直接判分的采分点。",
                    "检查材料题是否有材料或来源依据。",
                    "检查题干是否清楚、是否有超纲术语或上下文串扰。",
                    "输出可执行的修正建议，不直接自动改写已发布规则。"
            ),
            Map.of(
                    "required", List.of("teacherExamResult"),
                    "optional", List.of("rulePackId", "sourceEvidence", "userFeedback")
            ),
            Map.of(
                    "requiredSections", List.of("qualityFindings", "repairSuggestions", "acceptanceSignals"),
                    "draftAllowed", true
            ),
            List.of(
                    Map.of("id", "answer_rubric", "label", "答案采分点", "rule", "所有主观题必须可判分。"),
                    Map.of("id", "material_grounding", "label", "材料来源", "rule", "材料题必须可追溯。"),
                    Map.of("id", "self_improve_draft", "label", "优化草案", "rule", "只能生成待审核草案，不自动发布。")
            )
    );

    private TeacherSkillDefinitionService() {
    }

    private static final List<String> DEFAULT_BINDINGS = List.of(CLASSIC_EXAM_LOOP_ID, EXAM_REVIEW_ID);
    private static final CopyOnWriteArrayList<String> ACTIVE_BINDINGS = new CopyOnWriteArrayList<>(DEFAULT_BINDINGS);

    public static List<TeacherSkillDefinition> listBuiltInSkills() {
        return List.of(CLASSIC_EXAM_LOOP, EXAM_REVIEW);
    }

    public static TeacherSkillDefinition getBuiltInSkill(String id) {
        return listBuiltInSkills().stream()
                .filter(skill -> skill.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static List<String> defaultBindings() {
        return List.copyOf(DEFAULT_BINDINGS);
    }

    public static List<String> activeBindings() {
        return List.copyOf(ACTIVE_BINDINGS);
    }

    public static void setActiveBindings(List<String> ids) {
        List<String> validated = validateBindings(ids);
        ACTIVE_BINDINGS.clear();
        ACTIVE_BINDINGS.addAll(validated);
    }

    public static void resetActiveBindings() {
        ACTIVE_BINDINGS.clear();
        ACTIVE_BINDINGS.addAll(DEFAULT_BINDINGS);
    }

    public static boolean hasBindingOverride() {
        return !ACTIVE_BINDINGS.equals(DEFAULT_BINDINGS);
    }

    public static List<TeacherSkillDefinition> activeSkillDefinitions() {
        return activeBindings().stream()
                .map(TeacherSkillDefinitionService::getBuiltInSkill)
                .filter(skill -> skill != null)
                .toList();
    }

    public static List<String> validateBindings(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Teacher skill bindings cannot be empty");
        }
        List<String> distinct = ids.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("Teacher skill bindings cannot be empty");
        }
        for (String id : distinct) {
            if (getBuiltInSkill(id) == null) {
                throw new IllegalArgumentException("Unknown Teacher skill: " + id);
            }
        }
        return distinct;
    }

    public static String activeSkillPromptRules() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 当前 Teacher Skill 执行流程\n");
        for (TeacherSkillDefinition skill : activeSkillDefinitions()) {
            sb.append("- ").append(skill.name()).append("（").append(skill.id()).append("）：")
                    .append(skill.purpose()).append("\n");
            for (String step : skill.workflowSteps()) {
                sb.append("  - ").append(step).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
