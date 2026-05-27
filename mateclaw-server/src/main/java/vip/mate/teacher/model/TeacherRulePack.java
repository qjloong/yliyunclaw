package vip.mate.teacher.model;

import java.util.List;
import java.util.Map;

/**
 * Runtime-facing Teacher rule pack contract.
 *
 * <p>Phase 4 keeps this immutable and template-backed. Phase 6 can add
 * workspace/admin overrides without changing the prompt-facing shape.</p>
 */
public record TeacherRulePack(
        String id,
        String stage,
        String subject,
        String module,
        String name,
        String version,
        Map<String, Object> defaultQuestionMix,
        List<QuestionTypeRule> questionTypeRules,
        List<String> hardRules,
        List<Map<String, String>> sourceRequirements,
        List<Map<String, String>> acceptanceMatrix
) {
}
