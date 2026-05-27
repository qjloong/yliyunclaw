package vip.mate.teacher.model;

/**
 * Configurable scoring and generation constraints for one Teacher question type.
 */
public record QuestionTypeRule(
        String type,
        String displayName,
        String defaultScore,
        String generationRule,
        boolean requiresAnswer,
        boolean requiresScoringRubric,
        boolean requiresMaterial
) {
}
