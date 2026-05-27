package vip.mate.teacher.model;

/**
 * RulePack API view with override state.
 */
public record TeacherRulePackView(
        TeacherRulePack rulePack,
        boolean overridden,
        boolean workspaceOverridden,
        boolean globalOverridden,
        Long workspaceId,
        TeacherRulePack builtIn
) {
}
