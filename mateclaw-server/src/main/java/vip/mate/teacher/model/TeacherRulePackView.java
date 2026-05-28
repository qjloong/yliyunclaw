package vip.mate.teacher.model;

/**
 * RulePack API view with override state.
 */
public record TeacherRulePackView(
        TeacherRulePack rulePack,
        boolean overridden,
        TeacherRulePack builtIn
) {
}
