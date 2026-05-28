package vip.mate.teacher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.exception.MateClawException;
import vip.mate.harness.model.HarnessRun;
import vip.mate.harness.service.HarnessRunService;
import vip.mate.system.service.SystemSettingService;
import vip.mate.teacher.model.TeacherImprovementDraft;
import vip.mate.teacher.model.TeacherRulePack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlled Teacher self-improvement draft service.
 *
 * <p>Drafts are stored in system settings to keep Phase 6 lightweight. They are
 * reviewable proposals only; generation does not mutate active RulePacks.</p>
 */
@Service
@RequiredArgsConstructor
public class TeacherImprovementDraftService {

    public static final String DRAFTS_SETTING_KEY = "teacher.improvement.drafts";
    private static final int MAX_DRAFTS = 100;

    private final ObjectMapper objectMapper;
    private final SystemSettingService systemSettingService;
    private final HarnessRunService harnessRunService;

    public List<TeacherImprovementDraft> list() {
        return loadDrafts();
    }

    public TeacherImprovementDraft createFromHarnessRun(String runId, Long workspaceId, String note) {
        if (runId == null || runId.isBlank()) {
            throw new MateClawException(400, "Harness run id is required");
        }
        HarnessRun run = harnessRunService.get(runId);
        if (run == null) {
            throw new MateClawException(404, "Harness run not found: " + runId);
        }
        Map<String, Object> mockAcceptance = normalizeMap(run.getMetadata().get("mockAcceptance"));
        Map<String, Object> signals = normalizeMap(mockAcceptance.get("signals"));
        List<String> gateBlockers = toStringList(mockAcceptance.get("gateBlockers"));
        List<String> missingItems = toStringList(mockAcceptance.get("missingItems"));

        String effectiveRulePackId = asString(signals.get("teacherRulePackId"), TeacherRulePackService.CLASSIC_READING_V2_ID);
        // T2-2-10e: 运行时去 workspace 化
        TeacherRulePack basePack = TeacherRulePackService.effectiveRulePack(effectiveRulePackId);
        TeacherRulePack proposed = proposeRulePack(basePack, gateBlockers, missingItems, signals);
        Map<String, Object> diagnosis = buildDiagnosis(gateBlockers, missingItems, signals);
        Map<String, Object> skillPatch = buildSkillPatch(diagnosis, gateBlockers, missingItems, signals);
        Map<String, Object> acceptanceCase = buildAcceptanceCase(mockAcceptance, diagnosis, gateBlockers, missingItems, signals);

        TeacherImprovementDraft draft = new TeacherImprovementDraft();
        draft.setId("teacher-draft-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setStatus("pending");
        draft.setTargetType("rule_pack");
        draft.setTargetArea(asString(diagnosis.get("targetArea"), "rule_pack"));
        draft.setProposalType(resolveProposalType(diagnosis));
        draft.setRiskLevel(resolveRiskLevel(diagnosis, gateBlockers));
        draft.setTitle(resolveTitle(mockAcceptance, gateBlockers, missingItems));
        draft.setSummary(buildSummary(mockAcceptance, gateBlockers, missingItems, note));
        draft.setSourceRunId(run.getId());
        draft.setSourceConversationId(run.getConversationId());
        draft.setRulePackId(basePack != null ? basePack.id() : effectiveRulePackId);
        draft.setWorkspaceId(workspaceId);
        draft.setCreatedAt(LocalDateTime.now().toString());
        draft.setDiagnosis(diagnosis);
        draft.setEvidence(buildEvidence(mockAcceptance, signals, note));
        draft.setProposedPatch(buildPatch(basePack, proposed, gateBlockers, missingItems));
        draft.setProposedSkillPatch(skillPatch);
        draft.setProposedAcceptanceCase(acceptanceCase);
        draft.setProposedRulePack(proposed);

        List<TeacherImprovementDraft> drafts = new ArrayList<>(loadDrafts());
        drafts.add(0, draft);
        if (drafts.size() > MAX_DRAFTS) {
            drafts = drafts.subList(0, MAX_DRAFTS);
        }
        saveDrafts(drafts);
        return draft;
    }

    public TeacherImprovementDraft reject(String id, String note) {
        return review(id, "rejected", note, false, null, null);
    }

    public TeacherImprovementDraft accept(String id, String note, boolean publish, String scope, Long workspaceId) {
        return review(id, "accepted", note, publish, scope == null ? "workspace" : scope, workspaceId);
    }

    private TeacherImprovementDraft review(String id, String status, String note, boolean publish, String scope, Long workspaceId) {
        List<TeacherImprovementDraft> drafts = new ArrayList<>(loadDrafts());
        for (TeacherImprovementDraft draft : drafts) {
            if (!draft.getId().equals(id)) {
                continue;
            }
            if (!"pending".equalsIgnoreCase(draft.getStatus())) {
                throw new MateClawException(400, "Draft is already reviewed");
            }
            draft.setStatus(status);
            draft.setReviewedAt(LocalDateTime.now().toString());
            draft.setReviewNote(note != null ? note : "");
            if (publish && "accepted".equals(status)) {
                publishRulePack(draft, scope, workspaceId);
            }
            saveDrafts(drafts);
            return draft;
        }
        throw new MateClawException(404, "Teacher improvement draft not found: " + id);
    }

    private void publishRulePack(TeacherImprovementDraft draft, String scope, Long workspaceId) {
        TeacherRulePack proposed = draft.getProposedRulePack();
        if (proposed == null || proposed.id() == null || proposed.id().isBlank()) {
            throw new MateClawException(400, "Draft has no publishable RulePack");
        }
        try {
            String json = objectMapper.writeValueAsString(proposed);
            if ("global".equalsIgnoreCase(scope) || workspaceId == null) {
                systemSettingService.saveRawValue(
                        TeacherRulePackService.OVERRIDE_SETTING_PREFIX + proposed.id(),
                        json,
                        "Teacher improvement draft global publish: " + draft.getId()
                );
                TeacherRulePackService.putOverride(proposed);
            } else {
                systemSettingService.saveRawValue(
                        TeacherRulePackService.WORKSPACE_OVERRIDE_SETTING_PREFIX + workspaceId + "." + proposed.id(),
                        json,
                        "Teacher improvement draft workspace publish: " + draft.getId()
                );
                TeacherRulePackService.putWorkspaceOverride(workspaceId, proposed);
            }
        } catch (MateClawException e) {
            throw e;
        } catch (Exception e) {
            throw new MateClawException(500, "Failed to publish improvement draft: " + e.getMessage());
        }
    }

    private TeacherRulePack proposeRulePack(TeacherRulePack basePack,
                                            List<String> gateBlockers,
                                            List<String> missingItems,
                                            Map<String, Object> signals) {
        if (basePack == null) {
            return null;
        }
        List<String> hardRules = new ArrayList<>(basePack.hardRules() != null ? basePack.hardRules() : List.of());
        addRuleIfMissing(hardRules, "正式出题结果必须分为试题、参考答案、采分点、命题质量审核、来源依据五个区块。");
        if (containsAny(gateBlockers, "answer", "rubric") || containsAny(missingItems, "答案", "采分", "rubric")) {
            addRuleIfMissing(hardRules, "主观题、开放题、辩论题必须提供参考答案和可操作的采分点，不得只给命题说明。");
        }
        if (containsAny(gateBlockers, "source", "grounding") || containsAny(missingItems, "来源", "材料")) {
            addRuleIfMissing(hardRules, "材料题必须给出材料文本或明确来源依据；没有材料时应先要求补充或说明无法基于材料生成。");
        }
        if (Boolean.TRUE.equals(signals.get("teacherStructuredGateFailed"))) {
            addRuleIfMissing(hardRules, "输出不满足结构化区块时应优先修复格式，不继续扩展新题。");
        }
        if (containsAny(gateBlockers, "score labels") || Boolean.FALSE.equals(signals.get("teacherMentionsScore"))) {
            addRuleIfMissing(hardRules, "每道题必须标注建议分值，且分值应符合当前题型规则包的阅卷习惯。");
        }
        if (containsAny(gateBlockers, "open question") || Boolean.FALSE.equals(signals.get("teacherOpenQuestionHasDirection"))) {
            addRuleIfMissing(hardRules, "开放题、探究题、辩论题和批注题必须给出参考方向、作答依据和可判分采分点。");
        }
        if (containsAny(gateBlockers, "micro-writing") || Boolean.FALSE.equals(signals.get("teacherMicroWritingExtraOnly"))) {
            addRuleIfMissing(hardRules, "微写作只能作为附加或选做任务，不得替代阅读理解主任务。");
        }
        if (containsAny(gateBlockers, "translation") || Boolean.FALSE.equals(signals.get("teacherTranslationRubric"))) {
            addRuleIfMissing(hardRules, "文言翻译题必须列出关键字词、特殊句式、直译要求和逐点采分标准。");
        }
        if (containsAny(gateBlockers, "modern reading source") || Boolean.FALSE.equals(signals.get("teacherModernSourceAuthenticity"))) {
            addRuleIfMissing(hardRules, "现代文阅读材料必须说明真实可靠来源；无法确认来源时必须提示补充材料，不得编造。");
        }
        if (containsAny(gateBlockers, "directory scan") || asInt(signals.get("teacherDirectoryTraversalToolCount")) > 1) {
            addRuleIfMissing(hardRules, "同一工作区连续会话应复用资料索引缓存，不得重复遍历目录。");
        }
        return new TeacherRulePack(
                basePack.id(),
                basePack.stage(),
                basePack.subject(),
                basePack.module(),
                basePack.name(),
                nextDraftVersion(basePack.version()),
                basePack.defaultQuestionMix(),
                basePack.questionTypeRules(),
                hardRules,
                basePack.sourceRequirements(),
                basePack.acceptanceMatrix()
        );
    }

    private Map<String, Object> buildPatch(TeacherRulePack basePack,
                                           TeacherRulePack proposed,
                                           List<String> gateBlockers,
                                           List<String> missingItems) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("kind", "rule_pack_patch");
        patch.put("rulePackId", proposed != null ? proposed.id() : TeacherRulePackService.CLASSIC_READING_V2_ID);
        patch.put("baseVersion", basePack != null ? basePack.version() : "");
        patch.put("proposedVersion", proposed != null ? proposed.version() : "");
        patch.put("gateBlockers", gateBlockers);
        patch.put("missingItems", missingItems);
        if (basePack != null && proposed != null) {
            int baseRules = basePack.hardRules() != null ? basePack.hardRules().size() : 0;
            int proposedRules = proposed.hardRules() != null ? proposed.hardRules().size() : 0;
            patch.put("addedHardRules", Math.max(0, proposedRules - baseRules));
        }
        return patch;
    }

    private Map<String, Object> buildDiagnosis(List<String> gateBlockers,
                                               List<String> missingItems,
                                               Map<String, Object> signals) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        String targetArea = "rule_pack";
        if (containsAny(gateBlockers, "directory scan", "source grounding", "source basis")
                || containsAny(missingItems, "来源", "材料", "知识库")) {
            targetArea = "knowledge_context";
        } else if (containsAny(gateBlockers, "teacher result", "answer", "rubric", "quality review", "score labels")
                || Boolean.TRUE.equals(signals.get("teacherStructuredGateFailed"))) {
            targetArea = "teacher_skill";
        } else if (containsAny(gateBlockers, "modern reading", "translation", "open question", "micro-writing")
                || !toStringList(signals.get("teacherRulePackGateBlockers")).isEmpty()) {
            targetArea = "rule_pack";
        } else if (!missingItems.isEmpty()) {
            targetArea = "acceptance_case";
        }
        diagnosis.put("targetArea", targetArea);
        diagnosis.put("rulePackId", asString(signals.get("teacherRulePackId"), TeacherRulePackService.CLASSIC_READING_V2_ID));
        diagnosis.put("rulePackName", asString(signals.get("teacherRulePackName"), ""));
        diagnosis.put("module", asString(signals.get("teacherRulePackModule"), ""));
        diagnosis.put("primaryCause", resolvePrimaryCause(targetArea, gateBlockers, missingItems));
        diagnosis.put("recommendedAction", resolveRecommendedAction(targetArea));
        diagnosis.put("signals", signals);
        return diagnosis;
    }

    private Map<String, Object> buildSkillPatch(Map<String, Object> diagnosis,
                                                List<String> gateBlockers,
                                                List<String> missingItems,
                                                Map<String, Object> signals) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("kind", "teacher_skill_patch");
        patch.put("targetArea", diagnosis.getOrDefault("targetArea", "teacher_skill"));
        patch.put("title", "补强 Teacher 出题闭环执行步骤");
        List<String> steps = new ArrayList<>();
        if (containsAny(gateBlockers, "teacher result", "answer", "rubric", "quality review")
                || containsAny(missingItems, "答案", "采分", "审核")) {
            steps.add("正式生成前检查输出区块：试题、参考答案、采分点、命题质量审核、来源依据。");
            steps.add("若缺少答案或采分点，先补齐对应题号，不继续扩展新题。");
        }
        if (containsAny(gateBlockers, "source", "directory scan") || containsAny(missingItems, "来源", "材料")) {
            steps.add("资料不足时优先提示知识库导入/绑定或审批读取，不重复遍历同一目录。");
        }
        if (containsAny(gateBlockers, "score labels") || Boolean.FALSE.equals(signals.get("teacherMentionsScore"))) {
            steps.add("生成后逐题检查题型、考点、难度和建议分值是否完整。");
        }
        if (steps.isEmpty()) {
            steps.add("根据 Harness 失败项生成复盘清单，并要求下一轮正式出题前逐项自检。");
        }
        patch.put("steps", steps);
        patch.put("risk", "pending_review");
        patch.put("publishable", false);
        patch.put("note", "当前阶段只生成 Skill patch 草案，不自动写入运行时 Skill。");
        return patch;
    }

    private Map<String, Object> buildAcceptanceCase(Map<String, Object> mockAcceptance,
                                                    Map<String, Object> diagnosis,
                                                    List<String> gateBlockers,
                                                    List<String> missingItems,
                                                    Map<String, Object> signals) {
        Map<String, Object> acceptance = new LinkedHashMap<>();
        acceptance.put("kind", "teacher_acceptance_case");
        acceptance.put("title", "Teacher 回归用例：" + resolvePrimaryCause(
                asString(diagnosis.get("targetArea"), "teacher_skill"), gateBlockers, missingItems));
        acceptance.put("sourceTaskTitle", mockAcceptance.getOrDefault("taskTitle", ""));
        acceptance.put("rulePackId", diagnosis.getOrDefault("rulePackId", ""));
        acceptance.put("module", diagnosis.getOrDefault("module", ""));
        List<String> checks = new ArrayList<>();
        checks.addAll(gateBlockers);
        checks.addAll(missingItems);
        if (checks.isEmpty()) {
            checks.add("输出必须满足 TeacherExamResultV2 分块结构。");
            checks.add("答案、采分点、来源依据和质量审核必须完整。");
        }
        acceptance.put("checks", checks.stream().distinct().limit(8).toList());
        acceptance.put("signals", Map.of(
                "score", mockAcceptance.getOrDefault("score", 0),
                "status", mockAcceptance.getOrDefault("status", ""),
                "teacherRulePackId", signals.getOrDefault("teacherRulePackId", "")
        ));
        acceptance.put("publishable", false);
        return acceptance;
    }

    private Map<String, Object> buildEvidence(Map<String, Object> mockAcceptance,
                                              Map<String, Object> signals,
                                              String note) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("status", mockAcceptance.getOrDefault("status", ""));
        evidence.put("score", mockAcceptance.getOrDefault("score", 0));
        evidence.put("confidence", mockAcceptance.getOrDefault("confidence", ""));
        evidence.put("gateBlockers", mockAcceptance.getOrDefault("gateBlockers", List.of()));
        evidence.put("missingItems", mockAcceptance.getOrDefault("missingItems", List.of()));
        evidence.put("signals", signals);
        evidence.put("reviewInput", note != null ? note : "");
        return evidence;
    }

    private String resolveTitle(Map<String, Object> mockAcceptance,
                                List<String> gateBlockers,
                                List<String> missingItems) {
        String taskTitle = String.valueOf(mockAcceptance.getOrDefault("taskTitle", "")).trim();
        if (!taskTitle.isBlank()) {
            return "Teacher 优化草案：" + taskTitle;
        }
        if (!gateBlockers.isEmpty()) {
            return "Teacher 优化草案：修复 " + gateBlockers.get(0);
        }
        if (!missingItems.isEmpty()) {
            return "Teacher 优化草案：补齐 " + missingItems.get(0);
        }
        return "Teacher 优化草案：规则包增强";
    }

    private String buildSummary(Map<String, Object> mockAcceptance,
                                List<String> gateBlockers,
                                List<String> missingItems,
                                String note) {
        List<String> parts = new ArrayList<>();
        Object status = mockAcceptance.get("status");
        Object score = mockAcceptance.get("score");
        if (status != null || score != null) {
            parts.add("Harness 验收状态：" + String.valueOf(status) + "，分数：" + String.valueOf(score));
        }
        if (!gateBlockers.isEmpty()) {
            parts.add("阻断项：" + String.join("；", gateBlockers));
        }
        if (!missingItems.isEmpty()) {
            parts.add("缺失项：" + String.join("；", missingItems));
        }
        if (note != null && !note.isBlank()) {
            parts.add("人工备注：" + note.trim());
        }
        if (parts.isEmpty()) {
            parts.add("基于最近 Teacher Harness 运行生成的可审核优化建议。");
        }
        return String.join("\n", parts);
    }

    private static String resolvePrimaryCause(String targetArea, List<String> gateBlockers, List<String> missingItems) {
        if (!gateBlockers.isEmpty()) {
            return gateBlockers.get(0);
        }
        if (!missingItems.isEmpty()) {
            return missingItems.get(0);
        }
        return switch (targetArea) {
            case "knowledge_context" -> "资料来源或知识库上下文不足";
            case "acceptance_case" -> "验收项覆盖不足";
            case "rule_pack" -> "规则包约束需要补强";
            default -> "Teacher Skill 执行流程需要补强";
        };
    }

    private static String resolveRecommendedAction(String targetArea) {
        return switch (targetArea) {
            case "knowledge_context" -> "补充知识库导入/绑定提示和目录缓存复用策略";
            case "acceptance_case" -> "新增回归验收用例，防止同类问题复发";
            case "rule_pack" -> "补充 RulePack 硬规则或题型规则";
            default -> "补充 Teacher Skill 的生成后自检步骤";
        };
    }

    private static String resolveProposalType(Map<String, Object> diagnosis) {
        String targetArea = asString(diagnosis.get("targetArea"), "rule_pack");
        return switch (targetArea) {
            case "teacher_skill" -> "skill_patch";
            case "acceptance_case" -> "acceptance_case";
            default -> "rule_pack_patch";
        };
    }

    private static String resolveRiskLevel(Map<String, Object> diagnosis, List<String> gateBlockers) {
        String targetArea = asString(diagnosis.get("targetArea"), "");
        if ("knowledge_context".equals(targetArea) || containsAny(gateBlockers, "directory", "source", "材料")) {
            return "medium";
        }
        return gateBlockers.size() >= 3 ? "medium" : "low";
    }

    private List<TeacherImprovementDraft> loadDrafts() {
        String stored = systemSettingService.getRawValue(DRAFTS_SETTING_KEY, "");
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stored, new TypeReference<List<TeacherImprovementDraft>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void saveDrafts(List<TeacherImprovementDraft> drafts) {
        try {
            systemSettingService.saveRawValue(DRAFTS_SETTING_KEY, objectMapper.writeValueAsString(drafts),
                    "Teacher improvement drafts");
        } catch (Exception e) {
            throw new MateClawException(500, "Failed to save Teacher improvement drafts: " + e.getMessage());
        }
    }

    private static Map<String, Object> normalizeMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                if (key != null) {
                    result.put(String.valueOf(key), item);
                }
            });
        }
        return result;
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static boolean containsAny(List<String> values, String... needles) {
        String text = String.join(" ", values).toLowerCase();
        for (String needle : needles) {
            if (needle != null && text.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static void addRuleIfMissing(List<String> rules, String rule) {
        if (rules.stream().noneMatch(existing -> existing.equals(rule))) {
            rules.add(rule);
        }
    }

    private static String nextDraftVersion(String version) {
        String base = version == null || version.isBlank() ? "v1" : version;
        return base.contains("+draft") ? base : base + "+draft";
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
