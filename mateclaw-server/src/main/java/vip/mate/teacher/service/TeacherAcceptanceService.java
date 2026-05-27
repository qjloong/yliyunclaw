package vip.mate.teacher.service;

import org.springframework.stereotype.Service;
import vip.mate.teacher.model.QuestionTypeRule;
import vip.mate.teacher.model.TeacherRulePack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-pack aware Teacher acceptance checks for Harness mock tasks.
 *
 * <p>This intentionally stays heuristic: it turns Teacher v2 rule packs into
 * concrete scoring signals and blockers without calling another model.</p>
 */
@Service
public class TeacherAcceptanceService {

    public TeacherAcceptanceResult assess(String preview,
                                          boolean awaitingConfirmation,
                                          boolean structuredSections,
                                          boolean hasAnswerSection,
                                          boolean hasRubricSection,
                                          boolean hasSourceSection,
                                          int directoryTraversalToolCount,
                                          int materialIndexToolCount,
                                          int directoryCacheHitCount) {
        String normalized = normalize(preview);
        String rulePackId = resolveRulePackId(preview, normalized);
        TeacherRulePack pack = TeacherRulePackService.effectiveRulePack(rulePackId);
        if (pack == null) {
            pack = TeacherRulePackService.classicReadingV2();
            rulePackId = pack.id();
        }

        boolean formalResult = !awaitingConfirmation && !normalized.isBlank();
        List<String> detectedQuestionTypes = detectQuestionTypes(pack, normalized);
        boolean mentionsQuestionMix = mentionsQuestionMix(pack, normalized, detectedQuestionTypes);
        boolean mentionsScore = containsAny(normalized, "分值", "建议分值", "总分", "赋分")
                || java.util.regex.Pattern.compile("\\d+\\s*分").matcher(preview == null ? "" : preview).find();
        boolean mentionsMaterial = containsAny(normalized, "材料", "原文", "节选", "文本", "来源依据", "命题依据", "出处");
        boolean mentionsAnswerRubric = hasAnswerSection && hasRubricSection;
        boolean hasOpenQuestion = containsAny(normalized, "开放题", "探究题", "辩论", "批注", "微写作");
        boolean openQuestionHasDirection = !hasOpenQuestion
                || containsAny(normalized, "参考方向", "合理表达", "观点明确", "结合文本", "采分点", "评分标准");
        boolean microWritingExtraOnly = !containsAny(normalized, "微写作")
                || containsAny(normalized, "附加", "附加题", "拓展选做");
        boolean translationRubric = !containsAny(normalized, "翻译")
                || containsAny(normalized, "关键字词", "特殊句式", "采分点", "直译");
        boolean modernSourceAuthenticity = !TeacherRulePackService.MODERN_READING_V1_ID.equals(rulePackId)
                || containsAny(normalized, "来源真实", "真实可靠", "不编造", "出处", "来源依据");
        boolean directoryScanControlled = directoryTraversalToolCount + materialIndexToolCount <= 1
                || directoryCacheHitCount > 0
                || containsAny(normalized, "缓存", "不再重扫", "按需读取", "知识库导入", "绑定知识库");

        List<String> blockers = new ArrayList<>();
        if (formalResult && !structuredSections) {
            blockers.add("teacher result is not split into required sections");
        }
        if (formalResult && !mentionsScore) {
            blockers.add("missing per-question score labels");
        }
        if (formalResult && requiresMaterial(pack) && !mentionsMaterial && !hasSourceSection) {
            blockers.add("missing material or source basis required by rule pack");
        }
        if (formalResult && !mentionsAnswerRubric) {
            blockers.add("missing answer/rubric pair required by rule pack");
        }
        if (formalResult && !openQuestionHasDirection) {
            blockers.add("open question lacks answer direction or rubric");
        }
        if (formalResult && !microWritingExtraOnly) {
            blockers.add("micro-writing must be marked as extra-only");
        }
        if (formalResult && !translationRubric) {
            blockers.add("translation question lacks keyword/sentence-pattern rubric");
        }
        if (formalResult && !modernSourceAuthenticity) {
            blockers.add("modern reading source authenticity not stated");
        }
        if (!directoryScanControlled) {
            blockers.add("repeated directory scan without cache or alternative path");
        }

        List<String> evidence = new ArrayList<>();
        evidence.add("teacher rule pack=" + pack.name());
        if (!detectedQuestionTypes.isEmpty()) {
            evidence.add("question types=" + String.join(", ", detectedQuestionTypes.stream().limit(4).toList()));
        }
        if (mentionsQuestionMix) {
            evidence.add("rule-pack question mix signal");
        }
        if (mentionsScore) {
            evidence.add("score labels detected");
        }
        if (mentionsMaterial || hasSourceSection) {
            evidence.add("material/source signal detected");
        }
        if (openQuestionHasDirection && hasOpenQuestion) {
            evidence.add("open-answer direction detected");
        }
        if (directoryScanControlled) {
            evidence.add("directory scan controlled");
        }

        int scoreAdjustment = 0;
        if (formalResult && structuredSections) scoreAdjustment += 8;
        if (mentionsQuestionMix) scoreAdjustment += 6;
        if (mentionsScore) scoreAdjustment += 6;
        if (mentionsMaterial || hasSourceSection) scoreAdjustment += 6;
        if (mentionsAnswerRubric) scoreAdjustment += 8;
        if (hasOpenQuestion && openQuestionHasDirection) scoreAdjustment += 5;
        if (directoryScanControlled) scoreAdjustment += 4;
        scoreAdjustment -= blockers.size() * 8;

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("teacherRulePackId", rulePackId);
        signals.put("teacherRulePackName", pack.name());
        signals.put("teacherRulePackModule", pack.module());
        signals.put("teacherDetectedQuestionTypes", detectedQuestionTypes);
        signals.put("teacherRulePackQuestionTypeCount", pack.questionTypeRules() != null ? pack.questionTypeRules().size() : 0);
        signals.put("teacherMentionsQuestionMix", mentionsQuestionMix);
        signals.put("teacherMentionsScore", mentionsScore);
        signals.put("teacherMentionsMaterial", mentionsMaterial);
        signals.put("teacherOpenQuestionHasDirection", openQuestionHasDirection);
        signals.put("teacherMicroWritingExtraOnly", microWritingExtraOnly);
        signals.put("teacherTranslationRubric", translationRubric);
        signals.put("teacherModernSourceAuthenticity", modernSourceAuthenticity);
        signals.put("teacherDirectoryScanControlled", directoryScanControlled);
        signals.put("teacherRulePackGateBlockers", List.copyOf(blockers));

        return new TeacherAcceptanceResult(
                rulePackId,
                pack.module(),
                pack.name(),
                List.copyOf(blockers),
                List.copyOf(evidence),
                Map.copyOf(signals),
                scoreAdjustment
        );
    }

    private static String resolveRulePackId(String preview, String normalized) {
        String marker = extractMarker(preview, "teacher_rule_pack_id");
        if (!marker.isBlank()) {
            return marker;
        }
        if (containsAny(normalized, "classical_chinese", "文言文", "文言", "翻译题", "断句")) {
            return TeacherRulePackService.CLASSICAL_CHINESE_V1_ID;
        }
        if (containsAny(normalized, "modern_reading", "现代文", "散文", "小说", "说明文", "议论文", "非连续性文本")) {
            return TeacherRulePackService.MODERN_READING_V1_ID;
        }
        if (containsAny(normalized, "ancient_poetry", "古诗", "诗词", "默写", "炼字", "赏句", "意象")) {
            return TeacherRulePackService.ANCIENT_POETRY_V1_ID;
        }
        if (containsAny(normalized, "basic_knowledge", "基础知识", "字音", "字形", "成语", "病句", "文学常识", "综合性学习")) {
            return TeacherRulePackService.BASIC_KNOWLEDGE_V1_ID;
        }
        if (containsAny(normalized, "writing", "作文", "写作", "审题", "立意", "提纲", "片段写作")) {
            return TeacherRulePackService.WRITING_V1_ID;
        }
        return TeacherRulePackService.CLASSIC_READING_V2_ID;
    }

    private static String extractMarker(String text, String marker) {
        if (text == null || marker == null || marker.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(marker + "\\s*:\\s*([A-Za-z0-9_.-]+)")
                .matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static List<String> detectQuestionTypes(TeacherRulePack pack, String normalized) {
        List<String> detected = new ArrayList<>();
        if (pack.questionTypeRules() == null) {
            return detected;
        }
        for (QuestionTypeRule rule : pack.questionTypeRules()) {
            if (rule == null) {
                continue;
            }
            String key = normalize(rule.type());
            String displayName = normalize(rule.displayName());
            if ((!key.isBlank() && normalized.contains(key))
                    || (!displayName.isBlank() && normalized.contains(displayName))) {
                detected.add(rule.displayName());
            }
        }
        return detected;
    }

    private static boolean mentionsQuestionMix(TeacherRulePack pack, String normalized, List<String> detectedQuestionTypes) {
        if (containsAny(normalized, "题型比例", "题型分布", "题量与题型")) {
            return true;
        }
        if (detectedQuestionTypes.size() >= 2) {
            return true;
        }
        if (pack.defaultQuestionMix() == null) {
            return false;
        }
        return pack.defaultQuestionMix().values().stream()
                .map(String::valueOf)
                .map(TeacherAcceptanceService::normalize)
                .filter(value -> !value.isBlank())
                .anyMatch(normalized::contains);
    }

    private static boolean requiresMaterial(TeacherRulePack pack) {
        if (pack.questionTypeRules() == null) {
            return false;
        }
        return pack.questionTypeRules().stream()
                .filter(rule -> rule != null)
                .anyMatch(QuestionTypeRule::requiresMaterial);
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record TeacherAcceptanceResult(
            String rulePackId,
            String module,
            String rulePackName,
            List<String> gateBlockers,
            List<String> evidence,
            Map<String, Object> signals,
            int scoreAdjustment
    ) {
        public static TeacherAcceptanceResult empty() {
            return new TeacherAcceptanceResult("", "", "", List.of(), List.of(), Map.of(), 0);
        }
    }
}
